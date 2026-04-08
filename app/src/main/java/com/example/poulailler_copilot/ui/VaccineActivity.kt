package com.example.poulailler_copilot.ui

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
import com.example.poulailler_copilot.R
import com.example.poulailler_copilot.data.AppDatabase
import com.example.poulailler_copilot.data.VaccineEntry
import com.example.poulailler_copilot.databinding.ActivityVaccineBinding
import com.example.poulailler_copilot.databinding.DialogAddVaccineBinding
import com.example.poulailler_copilot.databinding.ItemVaccineBinding
import com.example.poulailler_copilot.repository.FirebaseRepository
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
        loadProphylaxisGuide()

        binding.fabAddVaccine.setOnClickListener {
            showAddVaccineDialog()
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
        adapter = VaccineAdapter { entry ->
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
                            if (entry.firestoreId != null) {
                                firebaseRepo.deleteVaccine(entry.firestoreId)
                            }
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@VaccineActivity, "Soin supprimé", Toast.LENGTH_SHORT).show()
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

    private fun loadProphylaxisGuide() {
        val guideHtml = """
            <font color='#FFFFFF'><b>J1 :</b></font> Bronchite Infectieuse (H120)<br/>
            <font color='#FFFFFF'><b>J7 :</b></font> Newcastle (HB1)<br/>
            <font color='#FFFFFF'><b>J14 :</b></font> Gumboro (Intermédiaire)<br/>
            <font color='#FFFFFF'><b>J21 :</b></font> Newcastle (La Sota) + Rappel Gumboro<br/>
            <font color='#FFFFFF'><b>S8 :</b></font> Typhose (Variole)<br/>
            <font color='#FFFFFF'><b>S12 :</b></font> Newcastle + BI (Inactivé)
        """.trimIndent()
        
        binding.tvProphylaxisInfo.text = Html.fromHtml(guideHtml, Html.FROM_HTML_MODE_LEGACY)
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
            R.id.nav_vaccines -> {}
            R.id.nav_expenses -> {
                val intent = Intent(this, ExpensesActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                intent.putExtra("selectedBatchId", selectedBatchId)
                startActivity(intent)
            }
            R.id.nav_sales -> {
                val intent = Intent(this, SalesActivity::class.java)
                intent.putExtra("userIdString", userId)
                intent.putExtra("role", userRole)
                intent.putExtra("selectedBatchId", selectedBatchId)
                startActivity(intent)
            }
            R.id.nav_mortality -> {
                val intent = Intent(this, MortalityActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                intent.putExtra("selectedBatchId", selectedBatchId)
                startActivity(intent)
            }
            R.id.nav_logout -> {
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
