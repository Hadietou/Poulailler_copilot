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
import com.example.poulailler_copilot.data.Expense
import com.example.poulailler_copilot.databinding.ActivityExpensesBinding
import com.example.poulailler_copilot.repository.FirebaseRepository
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class ExpensesActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityExpensesBinding
    private val calendar = Calendar.getInstance()
    private var selectedDateMs: Long = System.currentTimeMillis()
    private var currency: String = "MRU"
    private val firebaseRepo = FirebaseRepository()
    private var userRole: String = "AGENT"
    private var userId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExpensesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userRole = intent.getStringExtra("role") ?: "AGENT"
        userId = intent.getStringExtra("userIdString") ?: FirebaseAuth.getInstance().currentUser?.uid

        setupNavigation()
        loadCurrency()
        setupCategorySpinner()
        setupDatePicker()

        binding.btnSaveExpense.setOnClickListener {
            saveExpense()
        }

        binding.btnViewExpenseHistory.setOnClickListener {
            startActivity(Intent(this, ExpenseHistoryActivity::class.java))
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
            val info = firebaseRepo.getFarmInfo()
            withContext(Dispatchers.Main) {
                currency = info?.currency ?: "MRU"
                binding.tilAmount.hint = "Montant ($currency)"
            }
        }
    }

    private fun setupCategorySpinner() {
        val categories = arrayOf("Aliment", "Santé", "Transport", "Main d'œuvre", "Autre")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categories)
        binding.autoCompleteCategory.setAdapter(adapter)

        binding.autoCompleteCategory.setOnItemClickListener { _, _, position, _ ->
            if (categories[position] == "Aliment") {
                binding.tilQuantityKg.visibility = View.VISIBLE
            } else {
                binding.tilQuantityKg.visibility = View.GONE
            }
        }
    }

    private fun setupDatePicker() {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        binding.etExpenseDate.setText(sdf.format(Date()))

        binding.etExpenseDate.setOnClickListener {
            DatePickerDialog(this, { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                selectedDateMs = calendar.timeInMillis
                binding.etExpenseDate.setText(sdf.format(calendar.time))
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }
    }

    private fun saveExpense() {
        val category = binding.autoCompleteCategory.text.toString()
        val amount = binding.etAmount.text.toString().toDoubleOrNull() ?: 0.0
        val qty = binding.etQuantityKg.text.toString().toDoubleOrNull() ?: 0.0
        val description = binding.etDescription.text.toString()

        if (category.isEmpty() || amount <= 0.0) {
            Toast.makeText(this, "Veuillez remplir les champs obligatoires", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val expense = Expense(
                date = selectedDateMs,
                category = category,
                amount = amount,
                quantityKg = if (category == "Aliment") qty else 0.0,
                description = description
            )
            AppDatabase.getInstance(this@ExpensesActivity).expenseDao().insert(expense)
            firebaseRepo.addExpense(expense)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@ExpensesActivity, "Dépense enregistrée", Toast.LENGTH_SHORT).show()
                finish()
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
            R.id.nav_expenses -> {} // Already here
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
