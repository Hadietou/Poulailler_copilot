package com.example.poulailler_copilot.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import com.example.poulailler_copilot.R
import com.example.poulailler_copilot.data.AppDatabase
import com.example.poulailler_copilot.data.FarmInfo
import com.example.poulailler_copilot.databinding.ActivityFarmInfoBinding
import com.example.poulailler_copilot.repository.FirebaseRepository
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class FarmInfoActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityFarmInfoBinding
    private val calendar = Calendar.getInstance()
    private var arrivalDateMs: Long = 0
    private var birthDateMs: Long = 0
    private var currentFarmInfo: FarmInfo? = null
    private val firebaseRepo = FirebaseRepository()
    
    private val currencies = arrayOf("MRU", "CFA")
    private var userRole: String = "AGENT"
    private var userId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFarmInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userRole = intent.getStringExtra("role") ?: "AGENT"
        userId = intent.getStringExtra("userIdString") ?: FirebaseAuth.getInstance().currentUser?.uid

        setupNavigation()
        setupCurrencyDropdown()
        loadExistingData()
        setupDatePickers()

        binding.btnSaveFarmInfo.setOnClickListener {
            saveData()
        }

        binding.btnEditInfo.setOnClickListener {
            showEditMode(true)
        }

        binding.btnCancelEdit.setOnClickListener {
            showEditMode(false)
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
                    tvUsername.text = profile?.username?.uppercase() ?: "UTILISATEUR"
                    tvUserRole.text = userRole
                }
            }
        }
    }

    private fun setupCurrencyDropdown() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, currencies)
        binding.actvCurrency.setAdapter(adapter)
        binding.actvCurrency.setText(currencies[0], false)
    }

    private fun setupDatePickers() {
        binding.etArrivalDate.setOnClickListener {
            showDatePicker { date ->
                arrivalDateMs = date.time
                binding.etArrivalDate.setText(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date))
            }
        }

        binding.etBirthDate.setOnClickListener {
            showDatePicker { date ->
                birthDateMs = date.time
                binding.etBirthDate.setText(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date))
            }
        }
    }

    private fun loadExistingData() {
        lifecycleScope.launch {
            val info = firebaseRepo.getFarmInfo()
            withContext(Dispatchers.Main) {
                if (info != null && info.farmName.isNotEmpty()) {
                    currentFarmInfo = info
                    displayInfo(info)
                    showEditMode(false)
                } else {
                    showEditMode(true)
                }
            }
        }
    }

    private fun displayInfo(info: FarmInfo) {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        
        binding.tvDisplayFarmName.text = info.farmName
        binding.tvDisplayHensCount.text = "${info.hensCount} poules"
        binding.tvDisplayBreed.text = info.henBreed.ifEmpty { "Non spécifiée" }
        binding.tvDisplayArrival.text = if (info.arrivalDate > 0) sdf.format(Date(info.arrivalDate)) else "--/--/----"
        binding.tvDisplayBirth.text = if (info.chickBirthDate > 0) sdf.format(Date(info.chickBirthDate)) else "--/--/----"
        binding.tvDisplayCurrency.text = info.currency

        binding.etFarmName.setText(info.farmName)
        binding.etHensCount.setText(info.hensCount.toString())
        binding.etHenBreed.setText(info.henBreed)
        binding.actvCurrency.setText(info.currency, false)

        if (info.arrivalDate > 0) {
            arrivalDateMs = info.arrivalDate
            binding.etArrivalDate.setText(sdf.format(Date(info.arrivalDate)))
        }
        if (info.chickBirthDate > 0) {
            birthDateMs = info.chickBirthDate
            binding.etBirthDate.setText(sdf.format(Date(info.chickBirthDate)))
        }
    }

    private fun showEditMode(isEditing: Boolean) {
        if (isEditing) {
            binding.layoutDisplayInfo.visibility = View.GONE
            binding.layoutEditInfo.visibility = View.VISIBLE
            binding.btnCancelEdit.visibility = if (currentFarmInfo != null) View.VISIBLE else View.GONE
        } else {
            binding.layoutDisplayInfo.visibility = View.VISIBLE
            binding.layoutEditInfo.visibility = View.GONE
        }
    }

    private fun showDatePicker(onDateSelected: (Date) -> Unit) {
        DatePickerDialog(this, { _, year, month, dayOfMonth ->
            val cal = Calendar.getInstance()
            cal.set(year, month, dayOfMonth)
            onDateSelected(cal.time)
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun saveData() {
        val name = binding.etFarmName.text.toString()
        val count = binding.etHensCount.text.toString().toIntOrNull() ?: 0
        val breed = binding.etHenBreed.text.toString()
        val currency = binding.actvCurrency.text.toString()

        if (name.isEmpty() || count <= 0) {
            Toast.makeText(this, "Veuillez saisir le nom et le nombre de poules", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(this@FarmInfoActivity)
            val info = FarmInfo(
                id = 1,
                farmName = name,
                hensCount = count,
                henBreed = breed,
                arrivalDate = arrivalDateMs,
                chickBirthDate = birthDateMs,
                currency = currency
            )
            db.farmInfoDao().upsert(info)
            firebaseRepo.saveFarmInfo(info)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@FarmInfoActivity, "Informations enregistrées", Toast.LENGTH_SHORT).show()
                loadExistingData()
            }
        }
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
            R.id.nav_farm_info -> {}
            R.id.nav_collect -> {
                val intent = Intent(this, AgentActivity::class.java)
                intent.putExtra("userIdString", userId)
                intent.putExtra("role", userRole)
                startActivity(intent)
            }
            R.id.nav_vaccines -> {
                val intent = Intent(this, VaccineActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                startActivity(intent)
            }
            R.id.nav_expenses -> {
                val intent = Intent(this, ExpensesActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                startActivity(intent)
            }
            R.id.nav_sales -> {
                val intent = Intent(this, SalesActivity::class.java)
                intent.putExtra("userIdString", userId)
                intent.putExtra("role", userRole)
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
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}
