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
import com.example.poulailler_copilot.data.EggSale
import com.example.poulailler_copilot.databinding.ActivitySalesBinding
import com.example.poulailler_copilot.repository.FirebaseRepository
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
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
    private val firebaseRepo = FirebaseRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySalesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userId = intent.getStringExtra("userIdString") ?: FirebaseAuth.getInstance().currentUser?.uid
        userRole = intent.getStringExtra("role") ?: "AGENT"

        setupNavigation()
        loadCurrency()
        updateDateDisplay()

        binding.etSaleDate.setOnClickListener {
            showDatePicker()
        }

        binding.btnSaveSale.setOnClickListener {
            saveSale()
        }

        binding.btnViewSaleHistory.setOnClickListener {
            startActivity(Intent(this, SalesHistoryActivity::class.java))
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
                    tvUsername.text = profile?.username ?: "Utilisateur"
                    tvUserRole.text = userRole
                }
            }
        }
    }

    private fun loadCurrency() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(this@SalesActivity)
            val info = db.farmInfoDao().getInfo()
            withContext(Dispatchers.Main) {
                currency = info?.currency ?: "MRU"
                binding.tilUnitPrice.hint = "Prix unitaire ($currency)"
            }
        }
    }

    private fun showDatePicker() {
        DatePickerDialog(this, { _, year, month, day ->
            selectedDate.set(year, month, day)
            updateDateDisplay()
        }, selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH), selectedDate.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun updateDateDisplay() {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        binding.etSaleDate.setText(sdf.format(selectedDate.time))
    }

    private fun saveSale() {
        val quantity = binding.etQuantity.text.toString().toIntOrNull()
        val unitPrice = binding.etUnitPrice.text.toString().toDoubleOrNull()
        val buyer = binding.etBuyer.text.toString()
        val phone = binding.etPhoneNumber.text.toString()

        if (quantity == null || unitPrice == null) {
            Toast.makeText(this, "Veuillez remplir les champs obligatoires", Toast.LENGTH_SHORT).show()
            return
        }

        val totalPrice = quantity * unitPrice
        val sale = EggSale(
            userId = 0L, // Fallback for local Room
            date = selectedDate.timeInMillis,
            quantity = quantity,
            pricePerUnit = unitPrice,
            totalPrice = totalPrice,
            buyer = if (buyer.isBlank()) null else buyer,
            phoneNumber = if (phone.isBlank()) null else phone
        )

        lifecycleScope.launch(Dispatchers.IO) {
            AppDatabase.getInstance(this@SalesActivity).eggSaleDao().insert(sale)
            firebaseRepo.addSale(sale)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@SalesActivity, "Vente enregistrée !", Toast.LENGTH_LONG).show()
                binding.etQuantity.text?.clear()
                binding.etUnitPrice.text?.clear()
                binding.etBuyer.text?.clear()
                binding.etPhoneNumber.text?.clear()
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
            R.id.nav_sales -> {}
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
