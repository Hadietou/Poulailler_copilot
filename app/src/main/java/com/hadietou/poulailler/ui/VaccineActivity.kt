package com.hadietou.poulailler.ui

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.Html
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
import com.hadietou.poulailler.R
import com.hadietou.poulailler.BuildConfig
import com.hadietou.poulailler.data.AppDatabase
import com.hadietou.poulailler.data.VaccineEntry
import com.hadietou.poulailler.databinding.ActivityVaccineBinding
import com.hadietou.poulailler.databinding.DialogAddVaccineBinding
import com.hadietou.poulailler.databinding.ItemVaccineBinding
import com.hadietou.poulailler.repository.FirebaseRepository
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class VaccineActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityVaccineBinding
    private val calendar = Calendar.getInstance()
    private var selectedDateMs: Long = System.currentTimeMillis()
    private var userRole: String = "AGENT"
    private var userId: String? = null
    private var selectedBatchId: String? = null
    private val firebaseRepo = FirebaseRepository()
    private lateinit var adapter: VaccineAdapter
    
    private var allVaccines: List<VaccineEntry> = emptyList()
    private var isShowingAll = false
    private var isBlocked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVaccineBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userRole = intent.getStringExtra("role") ?: "AGENT"
        userId = intent.getStringExtra("userIdString") ?: FirebaseAuth.getInstance().currentUser?.uid
        selectedBatchId = intent.getStringExtra("selectedBatchId")

        setupNavigation()
        setupRecyclerView()
        observeVaccines()
        loadEnhancedSanitaryGuide()
        checkAccessStatus()

        binding.fabAddVaccine.setOnClickListener {
            if (isBlocked) { showBlockingDialog(); return@setOnClickListener }
            showAddVaccineDialog()
        }

        binding.btnShowMore.setOnClickListener {
            isShowingAll = true
            refreshDisplay()
        }
    }

    private fun checkAccessStatus() {
        lifecycleScope.launch {
            val blocked = firebaseRepo.isFarmAccessBlocked()
            isBlocked = blocked
            if (blocked) {
                withContext(Dispatchers.Main) {
                    binding.fabAddVaccine.visibility = View.GONE
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
        
        val menu = binding.navigationView.menu
        menu.findItem(R.id.nav_users)?.isVisible = userRole == "RESPONSABLE"
        menu.findItem(R.id.nav_expenses)?.isVisible = userRole == "RESPONSABLE"
        menu.findItem(R.id.nav_batches)?.isVisible = userRole == "RESPONSABLE"
        menu.findItem(R.id.nav_vaccines).isVisible = true

        updateNavHeader()
    }

    private fun setupRecyclerView() {
        adapter = VaccineAdapter { entry ->
            if (isBlocked) { showBlockingDialog(); return@VaccineAdapter }
            if (userRole == "RESPONSABLE") {
                showEditVaccineDialog(entry)
            }
        }
        binding.rvVaccineHistory.layoutManager = LinearLayoutManager(this)
        binding.rvVaccineHistory.adapter = adapter
    }

    private fun observeVaccines() {
        lifecycleScope.launch {
            firebaseRepo.getVaccinesFlow().collectLatest { list ->
                allVaccines = if (selectedBatchId != null) {
                    list.filter { it.batchId == selectedBatchId }
                } else {
                    list
                }
                refreshDisplay()
            }
        }
    }

    private fun refreshDisplay() {
        val toDisplay = if (isShowingAll) allVaccines else allVaccines.take(10)
        adapter.submitList(toDisplay)
        binding.btnShowMore.visibility = if (!isShowingAll && allVaccines.size > 10) View.VISIBLE else View.GONE
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

    private fun showAddVaccineDialog() {
        val dialogBinding = DialogAddVaccineBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        dialogBinding.btnSelectDate.text = "Date: ${sdf.format(Date())}"
        selectedDateMs = System.currentTimeMillis()

        dialogBinding.btnSelectDate.setOnClickListener {
            DatePickerDialog(this, { _, year, month, dayOfMonth ->
                val tempCal = Calendar.getInstance()
                tempCal.set(year, month, dayOfMonth)
                selectedDateMs = tempCal.timeInMillis
                dialogBinding.btnSelectDate.text = "Date: ${sdf.format(tempCal.time)}"
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        dialogBinding.btnSaveVaccine.setOnClickListener {
            val name = dialogBinding.etVaccineName.text.toString()
            val remarks = dialogBinding.etRemarks.text.toString()

            if (name.isEmpty()) {
                Toast.makeText(this, "Veuillez saisir le nom du soin", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val entry = VaccineEntry(
                        name = name,
                        date = selectedDateMs,
                        remarks = remarks,
                        batchId = selectedBatchId
                    )
                    firebaseRepo.addVaccine(entry)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@VaccineActivity, "Soin enregistré", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@VaccineActivity, e.message ?: "Erreur", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        dialog.show()
    }

    private fun showEditVaccineDialog(entry: VaccineEntry) {
        val dialogBinding = DialogAddVaccineBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.etVaccineName.setText(entry.name)
        dialogBinding.etRemarks.setText(entry.remarks ?: "")
        
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        dialogBinding.btnSelectDate.text = "Date: ${sdf.format(Date(entry.date))}"
        var editDateMs = entry.date

        dialogBinding.btnSelectDate.setOnClickListener {
            val dCal = Calendar.getInstance().apply { timeInMillis = entry.date }
            DatePickerDialog(this, { _, year, month, dayOfMonth ->
                val tempCal = Calendar.getInstance()
                tempCal.set(year, month, dayOfMonth)
                editDateMs = tempCal.timeInMillis
                dialogBinding.btnSelectDate.text = "Date: ${sdf.format(tempCal.time)}"
            }, dCal.get(Calendar.YEAR), dCal.get(Calendar.MONTH), dCal.get(Calendar.DAY_OF_MONTH)).show()
        }

        dialogBinding.btnSaveVaccine.text = "MODIFIER"
        dialogBinding.btnSaveVaccine.setOnClickListener {
            val name = dialogBinding.etVaccineName.text.toString()
            val remarks = dialogBinding.etRemarks.text.toString()

            if (name.isEmpty()) {
                Toast.makeText(this, "Nom obligatoire", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val updatedEntry = entry.copy(
                        date = editDateMs,
                        name = name,
                        remarks = remarks
                    )
                    firebaseRepo.updateVaccine(updatedEntry)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@VaccineActivity, "Soin modifié", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@VaccineActivity, e.message ?: "Erreur", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        val deleteButton = TextView(this).apply {
            text = "SUPPRIMER CE SOIN"
            setPadding(0, 48, 0, 0)
            setTextColor(getColor(R.color.error))
            gravity = android.view.Gravity.CENTER
            setOnClickListener {
                AlertDialog.Builder(this@VaccineActivity)
                    .setTitle("Suppression")
                    .setMessage("Voulez-vous supprimer cet enregistrement ?")
                    .setPositiveButton("Supprimer") { _, _ ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                if (entry.firestoreId != null) {
                                    firebaseRepo.deleteVaccine(entry.firestoreId)
                                }
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@VaccineActivity, "Soin supprimé", Toast.LENGTH_SHORT).show()
                                    dialog.dismiss()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@VaccineActivity, e.message ?: "Erreur", Toast.LENGTH_SHORT).show()
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

    private fun loadEnhancedSanitaryGuide() {
        val guideHtml = """
            <font color='#FFFFFF'><b>🗓️ PLAN DE VACCINATION TYPE (Hens):</b></font><br/>
            • <b>J1:</b> Bronchite Infectieuse (H120) + Marek (Hatchery)<br/>
            • <b>J7:</b> Newcastle (Peste aviaire) - Souche HB1<br/>
            • <b>J14:</b> Gumboro (1ère dose - Eau de boisson)<br/>
            • <b>J21:</b> Newcastle (Rappel La Sota) + Gumboro (Rappel)<br/>
            • <b>S8:</b> Typhose aviaire (Injection)<br/>
            • <b>S12:</b> Coryza infectieux (Prévention respiratoire)<br/>
            • <b>S16:</b> Newcastle + BI (Inactivé - Protection longue durée)<br/><br/>
            <font color='#FFD700'><b>⚠️ RAPPEL CHALEUR (Sahel):</b></font><br/>
            En Mauritanie, Mali, Sénégal, évitez de vacciner entre 11h et 16h. Utilisez de l'eau fraîche et ajoutez des anti-stress (Vitamines/Électrolytes) avant et après.<br/><br/>
            <font color='#4CAF50'><b>🧼 HYGIÈNE & BIOSÉCURITÉ:</b></font><br/>
            • <b>Pédiluve:</b> Indispensable à l'entrée avec désinfectant renouvelé.<br/>
            • <b>Lutte contre les vecteurs:</b> Grillage fin pour empêcher les oiseaux sauvages (porteurs de grippe aviaire).<br/>
            • <b>Alimentation:</b> Stockage au sec, loin des rongeurs.
        """.trimIndent()
        
        binding.tvProphylaxisInfo.text = Html.fromHtml(guideHtml, Html.FROM_HTML_MODE_LEGACY)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        if (isBlocked && item.itemId != R.id.nav_logout && item.itemId != R.id.nav_dashboard) {
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
            R.id.nav_collect -> {
                val intent = Intent(this, AgentActivity::class.java)
                intent.putExtra("userIdString", userId)
                intent.putExtra("role", userRole)
                startActivity(intent)
            }
            R.id.nav_vaccines -> {}
            R.id.nav_expenses -> {
                val intent = Intent(this, ExpensesActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                startActivity(intent)
            }
            R.id.nav_sales -> {
                val intent = Intent(this, SalesActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                startActivity(intent)
            }
            R.id.nav_mortality -> {
                val intent = Intent(this, MortalityActivity::class.java)
                intent.putExtra("userIdString", userId)
                intent.putExtra("role", userRole)
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
        checkAccessStatus()
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    class VaccineAdapter(private val onItemClick: (VaccineEntry) -> Unit) : RecyclerView.Adapter<VaccineAdapter.ViewHolder>() {
        private var items = listOf<VaccineEntry>()

        fun submitList(list: List<VaccineEntry>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemVaccineBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.bind(item)
            holder.itemView.setOnClickListener { onItemClick(item) }
        }

        override fun getItemCount() = items.size

        class ViewHolder(private val binding: ItemVaccineBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(item: VaccineEntry) {
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                binding.tvDate.text = sdf.format(Date(item.date))
                binding.tvVaccineName.text = item.name
                binding.tvRemarks.text = item.remarks ?: ""
            }
        }
    }
}
