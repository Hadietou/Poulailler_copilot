package com.hadietou.poulailler.ui

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hadietou.poulailler.R
import com.hadietou.poulailler.BuildConfig
import com.hadietou.poulailler.data.AppDatabase
import com.hadietou.poulailler.data.EggSale
import com.hadietou.poulailler.databinding.ActivitySalesBinding
import com.hadietou.poulailler.databinding.DialogAddSaleBinding
import com.hadietou.poulailler.databinding.ItemSaleBinding
import com.hadietou.poulailler.repository.FirebaseRepository
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import com.hadietou.poulailler.util.NavMenuStyler
import com.hadietou.poulailler.util.NetworkStatusMonitor

class SalesActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivitySalesBinding
    private var selectedDate: Calendar = Calendar.getInstance()
    private var userId: String? = null
    private var userRole: String = "AGENT"
    private var currency: String = "MRU"
    private var selectedBatchId: String? = null
    private val firebaseRepo = FirebaseRepository()
    private lateinit var adapter: SaleAdapter
    
    private var allSales: List<EggSale> = emptyList()
    private var isShowingAll = false
    private var isBlocked = false
    private var searchQuery: String = ""
    
    private var isEggMenuExpanded = true 
    private var isHealthMenuExpanded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySalesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        NetworkStatusMonitor.observe(this, binding.root)

        userId = intent.getStringExtra("userIdString") ?: FirebaseAuth.getInstance().currentUser?.uid
        userRole = intent.getStringExtra("role") ?: "AGENT"
        selectedBatchId = intent.getStringExtra("selectedBatchId")

        setupNavigation()
        loadCurrency()
        setupRecyclerView()
        observeSales()
        checkAccessStatus()

        binding.fabAddSale.setOnClickListener {
            if (isBlocked) { showBlockingDialog(); return@setOnClickListener }
            showAddSaleDialog()
        }

        binding.btnShowMore.setOnClickListener {
            isShowingAll = true
            refreshDisplay()
        }

        binding.etSearchSales.addTextChangedListener { text ->
            searchQuery = text?.toString().orEmpty()
            refreshDisplay()
        }
    }

    private fun checkAccessStatus() {
        lifecycleScope.launch {
            val blocked = firebaseRepo.isFarmAccessBlocked()
            isBlocked = blocked
            if (blocked) {
                withContext(Dispatchers.Main) {
                    binding.fabAddSale.visibility = View.GONE
                    showBlockingDialog()
                }
            }
        }
    }

    private fun showBlockingDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Mode Lecture Seule")
            .setMessage("Veuillez envoyer un email à hadietou@gmail.com pour lui demander de valider la ferme afin de continuer à utiliser l'application. En attendant, vous pouvez uniquement consulter vos données.")
            .setCancelable(true)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun setupNavigation() {
        setSupportActionBar(binding.toolbar)
        val toggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        binding.navigationView.setNavigationItemSelectedListener(this)

        rebuildDrawerMenu()
        updateNavHeader()
    }

    /**
     * NavigationView n'affiche pas toujours de manière fiable un item dont on vient
     * de changer la visibilité de groupe (menu.setGroupVisible) une fois déjà rendu à l'écran :
     * on force donc une reconstruction complète du menu avant de réappliquer les états courants.
     */
    private fun rebuildDrawerMenu() {
        val nav = binding.navigationView
        nav.menu.clear()
        nav.inflateMenu(R.menu.drawer_menu)
        val menu = nav.menu
        menu.findItem(R.id.nav_users)?.isVisible = userRole == "RESPONSABLE"
        menu.findItem(R.id.nav_expenses)?.isVisible = userRole == "RESPONSABLE"
        menu.findItem(R.id.nav_batches)?.isVisible = userRole == "RESPONSABLE"
        menu.setGroupVisible(R.id.group_egg_submenu, isEggMenuExpanded)
        menu.setGroupVisible(R.id.group_health_submenu, isHealthMenuExpanded)
        refreshDrawerMenuStyle()
    }

    private fun refreshDrawerMenuStyle() {
        NavMenuStyler.style(
            binding.navigationView,
            this,
            defaultIconTintRes = R.color.text_secondary,
            parents = listOf(
                R.id.nav_egg_management to isEggMenuExpanded,
                R.id.nav_health_management to isHealthMenuExpanded
            ),
            children = listOf(R.id.nav_collect, R.id.nav_sales, R.id.nav_vaccines, R.id.nav_mortality)
        )
    }

    private fun setupRecyclerView() {
        adapter = SaleAdapter(currency, { sale ->
            if (isBlocked) { showBlockingDialog(); return@SaleAdapter }
            if (userRole == "RESPONSABLE") {
                showEditSaleDialog(sale)
            }
        }, { sale ->
            if (isBlocked) { showBlockingDialog(); return@SaleAdapter }
            togglePaidStatus(sale)
        })
        binding.rvSalesHistory.layoutManager = LinearLayoutManager(this)
        binding.rvSalesHistory.adapter = adapter
    }

    private fun togglePaidStatus(sale: EggSale) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val updatedSale = sale.copy(isPaid = !sale.isPaid)
                firebaseRepo.updateSale(updatedSale)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SalesActivity, "Erreur statut : ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun observeSales() {
        lifecycleScope.launch {
            firebaseRepo.getSalesFlow().collectLatest { list ->
                allSales = if (selectedBatchId != null) {
                    list.filter { it.batchId == selectedBatchId }
                } else {
                    list
                }
                refreshDisplay()
            }
        }
    }

    private fun refreshDisplay() {
        val filtered = if (searchQuery.isBlank()) {
            allSales
        } else {
            allSales.filter { it.buyer?.contains(searchQuery, ignoreCase = true) == true }
        }
        val toDisplay = if (isShowingAll) filtered else filtered.take(10)
        adapter.updateCurrency(currency)
        adapter.submitList(toDisplay)
        binding.btnShowMore.visibility = if (!isShowingAll && filtered.size > 10) View.VISIBLE else View.GONE
        binding.tvEmptyState.text = if (allSales.isEmpty()) {
            "Aucune vente enregistrée pour le moment."
        } else {
            "Aucune vente ne correspond à la recherche."
        }
        binding.tvEmptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.rvSalesHistory.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun updateNavHeader() {
        val headerView = binding.navigationView.getHeaderView(0)
        val tvUsername = headerView.findViewById<TextView>(R.id.tvUsername)
        val tvUserRole = headerView.findViewById<TextView>(R.id.tvUserRole)
        val tvAppVersion = headerView.findViewById<TextView>(R.id.tvAppVersion)

        tvAppVersion?.text = "v${BuildConfig.VERSION_NAME}"

        lifecycleScope.launch {
            val uid = userId ?: FirebaseAuth.getInstance().currentUser?.uid
            if (uid != null) {
                val profile = firebaseRepo.getUserProfile(uid)
                withContext(Dispatchers.Main) {
                    tvUsername.text = profile?.username?.uppercase() ?: "UTILISATEUR"
                    tvUserRole.text = userRole
                }
            }
        }
    }

    private fun loadCurrency() {
        lifecycleScope.launch {
            val info = firebaseRepo.getFarmInfo()
            withContext(Dispatchers.Main) {
                currency = info?.currency ?: "MRU"
                adapter.updateCurrency(currency)
            }
        }
    }

    private fun showAddSaleDialog() {
        val dialogBinding = DialogAddSaleBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.tilUnitPrice.hint = "Prix par tablette ($currency)"
        
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        dialogBinding.etSaleDate.setText(sdf.format(selectedDate.time))
        selectedDate = Calendar.getInstance()

        dialogBinding.etSaleDate.setOnClickListener {
            DatePickerDialog(this, { _, year, month, day ->
                val tempCal = Calendar.getInstance()
                tempCal.set(year, month, day)
                selectedDate = tempCal
                dialogBinding.etSaleDate.setText(sdf.format(tempCal.time))
            }, selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH), selectedDate.get(Calendar.DAY_OF_MONTH)).show()
        }

        dialogBinding.btnSaveSale.setOnClickListener {
            val traysCount = dialogBinding.etQuantity.text.toString().toIntOrNull()
            val trayPrice = dialogBinding.etUnitPrice.text.toString().toDoubleOrNull()
            val buyer = dialogBinding.etBuyer.text.toString()
            val phone = dialogBinding.etPhoneNumber.text.toString()

            if (traysCount == null || trayPrice == null) {
                Toast.makeText(this, "Veuillez remplir les champs obligatoires", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val totalEggs = traysCount * 30
            val unitPriceEgg = trayPrice / 30.0
            val totalPrice = traysCount * trayPrice

            val sale = EggSale(
                userId = userId ?: "",
                date = selectedDate.timeInMillis,
                quantity = totalEggs,
                pricePerUnit = unitPriceEgg,
                totalPrice = totalPrice,
                buyer = if (buyer.isBlank()) null else buyer,
                phoneNumber = if (phone.isBlank()) null else phone,
                isPaid = false,
                batchId = selectedBatchId
            )

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    firebaseRepo.addSale(sale)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@SalesActivity, "Vente enregistrée !", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@SalesActivity, e.message ?: "Erreur", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        dialog.show()
    }

    private fun showEditSaleDialog(sale: EggSale) {
        val dialogBinding = DialogAddSaleBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.tilUnitPrice.hint = "Prix par tablette ($currency)"
        
        val trays = sale.quantity / 30
        val trayPrice = sale.pricePerUnit * 30

        dialogBinding.etQuantity.setText(trays.toString())
        dialogBinding.etUnitPrice.setText(trayPrice.toString())
        dialogBinding.etBuyer.setText(sale.buyer ?: "")
        dialogBinding.etPhoneNumber.setText(sale.phoneNumber ?: "")
        
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        dialogBinding.etSaleDate.setText(sdf.format(Date(sale.date)))
        var editDateMs = sale.date

        dialogBinding.etSaleDate.setOnClickListener {
            val dCal = Calendar.getInstance().apply { timeInMillis = sale.date }
            DatePickerDialog(this, { _, year, month, day ->
                val tempCal = Calendar.getInstance()
                tempCal.set(year, month, day)
                editDateMs = tempCal.timeInMillis
                dialogBinding.etSaleDate.setText(sdf.format(tempCal.time))
            }, dCal.get(Calendar.YEAR), dCal.get(Calendar.MONTH), dCal.get(Calendar.DAY_OF_MONTH)).show()
        }

        dialogBinding.btnSaveSale.text = "MODIFIER"
        dialogBinding.btnSaveSale.setOnClickListener {
            val traysCount = dialogBinding.etQuantity.text.toString().toIntOrNull()
            val trayPriceInput = dialogBinding.etUnitPrice.text.toString().toDoubleOrNull()
            val buyer = dialogBinding.etBuyer.text.toString()
            val phone = dialogBinding.etPhoneNumber.text.toString()

            if (traysCount == null || trayPriceInput == null) {
                Toast.makeText(this, "Champs obligatoires", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val totalEggs = traysCount * 30
            val unitPriceEgg = trayPriceInput / 30.0
            val totalPrice = traysCount * trayPriceInput

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val updatedSale = sale.copy(
                        date = editDateMs,
                        quantity = totalEggs,
                        pricePerUnit = unitPriceEgg,
                        totalPrice = totalPrice,
                        buyer = if (buyer.isBlank()) null else buyer,
                        phoneNumber = if (phone.isBlank()) null else phone
                    )
                    firebaseRepo.updateSale(updatedSale)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@SalesActivity, "Vente modifiée", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@SalesActivity, e.message ?: "Erreur", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        val deleteButton = TextView(this).apply {
            text = "SUPPRIMER CETTE VENTE"
            setPadding(0, 48, 0, 0)
            setTextColor(getColor(R.color.error))
            gravity = android.view.Gravity.CENTER
            setOnClickListener {
                AlertDialog.Builder(this@SalesActivity)
                    .setTitle("Suppression")
                    .setMessage("Voulez-vous vraiment supprimer cette vente ?")
                    .setPositiveButton("Supprimer") { _, _ ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                if (sale.firestoreId != null) {
                                    firebaseRepo.deleteSale(sale.firestoreId)
                                }
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@SalesActivity, "Vente supprimée", Toast.LENGTH_SHORT).show()
                                    dialog.dismiss()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@SalesActivity, e.message ?: "Erreur", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    .setNegativeButton("Annuler", null)
                    .show()
            }
        }
        (dialogBinding.root as ViewGroup).addView(deleteButton)

        dialog.show()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        if (isBlocked && item.itemId != R.id.nav_logout && item.itemId != R.id.nav_dashboard && item.itemId != R.id.nav_delete_account) {
            showBlockingDialog()
            return false
        }

        when (item.itemId) {
            R.id.nav_dashboard -> {
                val intent = Intent(this, DashboardActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                startActivity(intent)
                finish()
            }
            R.id.nav_batches -> {
                val intent = Intent(this, BatchActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                startActivity(intent)
            }
            R.id.nav_users -> {
                val intent = Intent(this, ResponsableActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                startActivity(intent)
            }
            
            R.id.nav_egg_management -> {
                isEggMenuExpanded = !isEggMenuExpanded
                if (isEggMenuExpanded) isHealthMenuExpanded = false
                rebuildDrawerMenu()
                return true
            }

            R.id.nav_health_management -> {
                isHealthMenuExpanded = !isHealthMenuExpanded
                if (isHealthMenuExpanded) isEggMenuExpanded = false
                rebuildDrawerMenu()
                return true
            }

            R.id.nav_collect -> {
                val intent = Intent(this, AgentActivity::class.java)
                intent.putExtra("userIdString", userId)
                intent.putExtra("role", userRole)
                intent.putExtra("selectedBatchId", selectedBatchId)
                startActivity(intent)
            }
            R.id.nav_vaccines -> {
                val intent = Intent(this, VaccineActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                intent.putExtra("selectedBatchId", selectedBatchId)
                startActivity(intent)
            }
            R.id.nav_expenses -> {
                val intent = Intent(this, ExpensesActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                intent.putExtra("selectedBatchId", selectedBatchId)
                startActivity(intent)
            }
            R.id.nav_settings -> {
                val intent = Intent(this, FarmInfoActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                startActivity(intent)
            }
            R.id.nav_sales -> {}
            R.id.nav_mortality -> {
                val intent = Intent(this, MortalityActivity::class.java)
                intent.putExtra("userIdString", userId)
                intent.putExtra("role", userRole)
                intent.putExtra("selectedBatchId", selectedBatchId)
                startActivity(intent)
            }
            R.id.nav_delete_account -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.delete_account_url)))
                startActivity(intent)
            }
            R.id.nav_logout -> {
                NavMenuStyler.confirmLogout(this) {
                    FirebaseAuth.getInstance().signOut()
                    val intent = Intent(this, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
            }
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onResume() {
        super.onResume()
        updateNavHeader()
        checkAccessStatus()
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    class SaleAdapter(
        private var currency: String,
        private val onItemClick: (EggSale) -> Unit,
        private val onStatusClick: (EggSale) -> Unit
    ) : RecyclerView.Adapter<SaleAdapter.ViewHolder>() {
        private var items = listOf<EggSale>()

        fun submitList(list: List<EggSale>) {
            items = list
            notifyDataSetChanged()
        }

        fun updateCurrency(newCurrency: String) {
            this.currency = newCurrency
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemSaleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.bind(item, currency, onStatusClick)
            holder.itemView.setOnClickListener { onItemClick(item) }
        }

        override fun getItemCount() = items.size

        class ViewHolder(private val binding: ItemSaleBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(item: EggSale, currency: String, onStatusClick: (EggSale) -> Unit) {
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                binding.tvDate.text = sdf.format(Date(item.date))
                binding.tvClient.text = item.buyer ?: "Client anonyme"
                
                val trays = item.quantity / 30
                val trayPrice = item.pricePerUnit * 30
                
                binding.tvDetails.text = String.format(Locale.getDefault(), "%d tablettes x %.0f", trays, trayPrice)
                binding.tvTotal.text = String.format(Locale.getDefault(), "%.0f %s", item.totalPrice, currency)

                if (item.isPaid) {
                    binding.ivPaidStatus.setImageResource(android.R.drawable.checkbox_on_background)
                    binding.ivPaidStatus.imageTintList = android.content.res.ColorStateList.valueOf(binding.root.context.getColor(R.color.emerald_soft))
                    binding.tvTotal.setTextColor(binding.root.context.getColor(R.color.emerald_soft))
                } else {
                    binding.ivPaidStatus.setImageResource(android.R.drawable.checkbox_off_background)
                    binding.ivPaidStatus.imageTintList = android.content.res.ColorStateList.valueOf(binding.root.context.getColor(R.color.error))
                    binding.tvTotal.setTextColor(binding.root.context.getColor(R.color.error))
                }

                binding.ivPaidStatus.setOnClickListener { onStatusClick(item) }
            }
        }
    }
}
