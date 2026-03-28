package com.example.poulailler_copilot.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import com.example.poulailler_copilot.R
import com.example.poulailler_copilot.data.AppDatabase
import com.example.poulailler_copilot.data.VaccineEntry
import com.example.poulailler_copilot.databinding.ActivityVaccineBinding
import com.example.poulailler_copilot.repository.FirebaseRepository
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
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
    private val firebaseRepo = FirebaseRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVaccineBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userRole = intent.getStringExtra("role") ?: "AGENT"
        userId = intent.getStringExtra("userIdString") ?: FirebaseAuth.getInstance().currentUser?.uid

        setupNavigation()
        setupDatePicker()

        binding.btnSaveVaccine.setOnClickListener {
            saveVaccine()
        }

        binding.btnViewVaccineHistory.setOnClickListener {
            startActivity(Intent(this, VaccineHistoryActivity::class.java))
        }

        loadProphylaxisGuide()
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

        updateNavHeader()
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
                    tvUsername.text = profile?.username ?: "Utilisateur"
                    tvUserRole.text = userRole
                }
            }
        }
    }

    private fun setupDatePicker() {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        binding.btnSelectDate.text = "Date: ${sdf.format(Date())}"

        binding.btnSelectDate.setOnClickListener {
            DatePickerDialog(this, { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                selectedDateMs = calendar.timeInMillis
                binding.btnSelectDate.text = "Date: ${sdf.format(calendar.time)}"
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }
    }

    private fun saveVaccine() {
        val name = binding.etVaccineName.text.toString()
        val remarks = binding.etRemarks.text.toString()

        if (name.isEmpty()) {
            Toast.makeText(this, "Veuillez saisir le nom du soin", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(this@VaccineActivity)
            val entry = VaccineEntry(
                name = name,
                date = selectedDateMs,
                remarks = remarks
            )
            db.vaccineEntryDao().insert(entry)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@VaccineActivity, "Soin enregistré", Toast.LENGTH_SHORT).show()
                binding.etVaccineName.setText("")
                binding.etRemarks.setText("")
            }
        }
    }

    private fun loadProphylaxisGuide() {
        binding.tvProphylaxisInfo.text = """
            - J1: Bronchite Infectieuse (H120)
            - J7: Newcastle (HB1)
            - J14: Gumboro (Intermédiaire)
            - J21: Newcastle (La Sota) + Rappel Gumboro
            - S8: Typhose (Variole)
            - S12: Newcastle + BI (Inactivé)
        """.trimIndent()
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
                startActivity(intent)
            }
            R.id.nav_vaccines -> {} // Already here
            R.id.nav_expenses -> {
                val intent = Intent(this, ExpensesActivity::class.java)
                intent.putExtra("userIdString", userId)
                intent.putExtra("role", userRole)
                startActivity(intent)
            }
            R.id.nav_sales -> {
                val intent = Intent(this, SalesActivity::class.java)
                intent.putExtra("userIdString", userId)
                intent.putExtra("role", userRole)
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
}
