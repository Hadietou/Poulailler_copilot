package com.example.poulailler_copilot.ui

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.poulailler_copilot.R
import com.example.poulailler_copilot.data.AppDatabase
import com.example.poulailler_copilot.data.EggSale
import com.example.poulailler_copilot.databinding.ActivitySalesBinding
import com.example.poulailler_copilot.databinding.DialogAddSaleBinding
import com.example.poulailler_copilot.databinding.ItemSaleBinding
import com.example.poulailler_copilot.repository.FirebaseRepository
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySalesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userId = intent.getStringExtra("userIdString") ?: FirebaseAuth.getInstance().currentUser?.uid
        userRole = intent.getStringExtra("role") ?: "AGENT"
        selectedBatchId = intent.getStringExtra("selectedBatchId")

        setupNavigation()
        loadCurrency()
        setupRecyclerView()
        observeSales()

        binding.fabAddSale.setOnClickListener {
            showAddSaleDialog()
        }

        binding.btnShowMore.setOnClickListener {
            isShowingAll = true
            refreshDisplay()
        }
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
        
        val menu = binding.navigationView.menu
        menu.findItem(R.id.nav_users).isVisible = userRole == "RESPONSABLE"
        menu.findItem(R.id.nav_expenses).isVisible = userRole == "RESPONSABLE"
        menu.findItem(R.id.nav_vaccines).isVisible = userRole == "RESPONSABLE"
        menu.findItem(R.id.nav_farm_info).isVisible = userRole == "RESPONSABLE"
        menu.findItem(R.id.nav_batches).isVisible = userRole == "RESPONSABLE"

        updateNavHeader()
    }

    private fun setupRecyclerView() {
        adapter = SaleAdapter(currency) { sale ->
            if (userRole == "RESPONSABLE") {
                showEditSaleDialog(sale)
            }
        }
        binding.rvSalesHistory.layoutManager = LinearLayoutManager(this)
        binding.rvSalesHistory.adapter = adapter
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
        val toDisplay = if (isShowingAll) allSales else allSales.take(10)
        adapter.updateCurrency(currency)
        adapter.submitList(toDisplay)
        binding.btnShowMore.visibility = if (!isShowingAll && allSales.size > 10) View.VISIBLE else View.GONE
    }

    private fun updateNavHeader() {
        val headerView = binding.navigationView.getHeaderView(0)
        val tvUsername = headerView.findViewById<TextView>(R.id.tvUsername)
        val tvUserRole = headerView.findViewById<TextView>(R.id.tvUserRole)

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

        dialogBinding.tilUnitPrice.hint = "Prix unitaire ($currency)"
        
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
            val quantity = dialogBinding.etQuantity.text.toString().toIntOrNull()
            val unitPrice = dialogBinding.etUnitPrice.text.toString().toDoubleOrNull()
            val buyer = dialogBinding.etBuyer.text.toString()
            val phone = dialogBinding.etPhoneNumber.text.toString()

            if (quantity == null || unitPrice == null) {
                Toast.makeText(this, "Veuillez remplir les champs obligatoires", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val totalPrice = quantity * unitPrice
            val sale = EggSale(
                userId = userId ?: "",
                date = selectedDate.timeInMillis,
                quantity = quantity,
                pricePerUnit = unitPrice,
                totalPrice = totalPrice,
                buyer = if (buyer.isBlank()) null else buyer,
                phoneNumber = if (phone.isBlank()) null else phone,
                batchId = selectedBatchId
            )

            lifecycleScope.launch(Dispatchers.IO) {
                firebaseRepo.addSale(sale)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SalesActivity, "Vente enregistrée !", Toast.LENGTH_LONG).show()
                    dialog.dismiss()
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

        dialogBinding.tilUnitPrice.hint = "Prix unitaire ($currency)"
        
        dialogBinding.etQuantity.setText(sale.quantity.toString())
        dialogBinding.etUnitPrice.setText(sale.pricePerUnit.toString())
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
            val quantity = dialogBinding.etQuantity.text.toString().toIntOrNull()
            val unitPrice = dialogBinding.etUnitPrice.text.toString().toDoubleOrNull()
            val buyer = dialogBinding.etBuyer.text.toString()
            val phone = dialogBinding.etPhoneNumber.text.toString()

            if (quantity == null || unitPrice == null) {
                Toast.makeText(this, "Champs obligatoires", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val totalPrice = quantity * unitPrice
            lifecycleScope.launch(Dispatchers.IO) {
                val updatedSale = sale.copy(
                    date = editDateMs,
                    quantity = quantity,
                    pricePerUnit = unitPrice,
                    totalPrice = totalPrice,
                    buyer = if (buyer.isBlank()) null else buyer,
                    phoneNumber = if (phone.isBlank()) null else phone
                )
                firebaseRepo.updateSale(updatedSale)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SalesActivity, "Vente modifiée", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
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
                            if (sale.firestoreId != null) {
                                firebaseRepo.deleteSale(sale.firestoreId)
                            }
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@SalesActivity, "Vente supprimée", Toast.LENGTH_SHORT).show()
                                dialog.dismiss()
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
            R.id.nav_farm_info -> {
                val intent = Intent(this, FarmInfoActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                startActivity(intent)
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
            R.id.nav_sales -> {}
            R.id.nav_mortality -> {
                val intent = Intent(this, MortalityActivity::class.java)
                intent.putExtra("userIdString", userId)
                intent.putExtra("role", userRole)
                intent.putExtra("selectedBatchId", selectedBatchId)
                startActivity(intent)
            }
            R.id.nav_logout -> {
                FirebaseAuth.getInstance().signOut()
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onResume() {
        super.onResume()
        updateNavHeader()
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    class SaleAdapter(private var currency: String, private val onItemClick: (EggSale) -> Unit) : RecyclerView.Adapter<SaleAdapter.ViewHolder>() {
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
            holder.bind(item, currency)
            holder.itemView.setOnClickListener { onItemClick(item) }
        }

        override fun getItemCount() = items.size

        class ViewHolder(private val binding: ItemSaleBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(item: EggSale, currency: String) {
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                binding.tvDate.text = sdf.format(Date(item.date))
                binding.tvClient.text = item.buyer ?: "Client anonyme"
                binding.tvDetails.text = "${item.quantity} œufs x ${String.format(Locale.getDefault(), "%.0f", item.pricePerUnit)}"
                binding.tvTotal.text = String.format(Locale.getDefault(), "%.0f %s", item.totalPrice, currency)
            }
        }
    }
}
