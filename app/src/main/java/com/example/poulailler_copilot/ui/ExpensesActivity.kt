package com.example.poulailler_copilot.ui

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
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
import com.example.poulailler_copilot.data.Expense
import com.example.poulailler_copilot.databinding.ActivityExpensesBinding
import com.example.poulailler_copilot.databinding.DialogAddExpenseBinding
import com.example.poulailler_copilot.databinding.ItemExpenseBinding
import com.example.poulailler_copilot.repository.FirebaseRepository
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class ExpensesActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityExpensesBinding
    private val calendar = Calendar.getInstance()
    private var selectedDateMs: Long = System.currentTimeMillis()
    private var currency: String = "MRU"
    private var selectedBatchId: String? = null
    private val firebaseRepo = FirebaseRepository()
    private var userRole: String = "AGENT"
    private var userId: String? = null
    private lateinit var adapter: ExpenseAdapter
    
    private var allExpenses: List<Expense> = emptyList()
    private var isShowingAll = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExpensesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userRole = intent.getStringExtra("role") ?: "AGENT"
        userId = intent.getStringExtra("userIdString") ?: FirebaseAuth.getInstance().currentUser?.uid
        selectedBatchId = intent.getStringExtra("selectedBatchId")

        setupNavigation()
        loadCurrency()
        setupRecyclerView()
        observeExpenses()

        binding.fabAddExpense.setOnClickListener {
            showAddExpenseDialog()
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
        adapter = ExpenseAdapter(currency) { expense ->
            if (userRole == "RESPONSABLE") {
                showEditExpenseDialog(expense)
            }
        }
        binding.rvExpenseHistory.layoutManager = LinearLayoutManager(this)
        binding.rvExpenseHistory.adapter = adapter
    }

    private fun observeExpenses() {
        lifecycleScope.launch {
            firebaseRepo.getExpensesFlow().collectLatest { list ->
                allExpenses = if (selectedBatchId != null) {
                    list.filter { it.batchId == selectedBatchId }
                } else {
                    list
                }
                refreshDisplay()
            }
        }
    }

    private fun refreshDisplay() {
        val toDisplay = if (isShowingAll) allExpenses else allExpenses.take(10)
        adapter.updateCurrency(currency)
        adapter.submitList(toDisplay)
        
        binding.btnShowMore.visibility = if (!isShowingAll && allExpenses.size > 10) View.VISIBLE else View.GONE
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

    private fun showAddExpenseDialog() {
        val dialogBinding = DialogAddExpenseBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.tilAmount.hint = "Montant ($currency)"
        
        val categories = arrayOf("Aliment", "Santé", "Transport", "Main d'œuvre", "Autre")
        val adapterSpinner = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categories)
        dialogBinding.autoCompleteCategory.setAdapter(adapterSpinner)

        dialogBinding.autoCompleteCategory.setOnItemClickListener { _, _, position, _ ->
            if (categories[position] == "Aliment") {
                dialogBinding.tilQuantityKg.visibility = View.VISIBLE
            } else {
                dialogBinding.tilQuantityKg.visibility = View.GONE
            }
        }

        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        dialogBinding.etExpenseDate.setText(sdf.format(Date()))
        selectedDateMs = System.currentTimeMillis()

        dialogBinding.etExpenseDate.setOnClickListener {
            DatePickerDialog(this, { _, year, month, dayOfMonth ->
                val tempCal = Calendar.getInstance()
                tempCal.set(year, month, dayOfMonth)
                selectedDateMs = tempCal.timeInMillis
                dialogBinding.etExpenseDate.setText(sdf.format(tempCal.time))
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        dialogBinding.btnSaveExpense.setOnClickListener {
            val category = dialogBinding.autoCompleteCategory.text.toString()
            val amount = dialogBinding.etAmount.text.toString().toDoubleOrNull() ?: 0.0
            val qty = dialogBinding.etQuantityKg.text.toString().toDoubleOrNull() ?: 0.0
            val description = dialogBinding.etDescription.text.toString()

            if (category.isEmpty() || amount <= 0.0) {
                Toast.makeText(this, "Veuillez remplir les champs obligatoires", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch(Dispatchers.IO) {
                val expense = Expense(
                    date = selectedDateMs,
                    category = category,
                    amount = amount,
                    quantityKg = if (category == "Aliment") qty else 0.0,
                    description = description,
                    batchId = selectedBatchId
                )
                firebaseRepo.addExpense(expense)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ExpensesActivity, "Dépense enregistrée", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }

    private fun showEditExpenseDialog(expense: Expense) {
        val dialogBinding = DialogAddExpenseBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.tilAmount.hint = "Montant ($currency)"
        
        val categories = arrayOf("Aliment", "Santé", "Transport", "Main d'œuvre", "Autre")
        val adapterSpinner = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categories)
        dialogBinding.autoCompleteCategory.setAdapter(adapterSpinner)

        dialogBinding.autoCompleteCategory.setText(expense.category, false)
        dialogBinding.etAmount.setText(expense.amount.toString())
        dialogBinding.etDescription.setText(expense.description ?: "")
        if (expense.category == "Aliment") {
            dialogBinding.tilQuantityKg.visibility = View.VISIBLE
            dialogBinding.etQuantityKg.setText(expense.quantityKg?.toString() ?: "")
        } else {
            dialogBinding.tilQuantityKg.visibility = View.GONE
        }

        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        dialogBinding.etExpenseDate.setText(sdf.format(Date(expense.date)))
        var editDateMs = expense.date

        dialogBinding.etExpenseDate.setOnClickListener {
            val dCal = Calendar.getInstance().apply { timeInMillis = expense.date }
            DatePickerDialog(this, { _, year, month, dayOfMonth ->
                val tempCal = Calendar.getInstance()
                tempCal.set(year, month, dayOfMonth)
                editDateMs = tempCal.timeInMillis
                dialogBinding.etExpenseDate.setText(sdf.format(tempCal.time))
            }, dCal.get(Calendar.YEAR), dCal.get(Calendar.MONTH), dCal.get(Calendar.DAY_OF_MONTH)).show()
        }

        dialogBinding.autoCompleteCategory.setOnItemClickListener { _, _, position, _ ->
            if (categories[position] == "Aliment") {
                dialogBinding.tilQuantityKg.visibility = View.VISIBLE
            } else {
                dialogBinding.tilQuantityKg.visibility = View.GONE
            }
        }

        dialogBinding.btnSaveExpense.text = "MODIFIER"
        dialogBinding.btnSaveExpense.setOnClickListener {
            val category = dialogBinding.autoCompleteCategory.text.toString()
            val amount = dialogBinding.etAmount.text.toString().toDoubleOrNull() ?: 0.0
            val qty = dialogBinding.etQuantityKg.text.toString().toDoubleOrNull() ?: 0.0
            val description = dialogBinding.etDescription.text.toString()

            if (category.isEmpty() || amount <= 0.0) {
                Toast.makeText(this, "Champs obligatoires manquants", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch(Dispatchers.IO) {
                val updatedExpense = expense.copy(
                    date = editDateMs,
                    category = category,
                    amount = amount,
                    quantityKg = if (category == "Aliment") qty else 0.0,
                    description = description
                )
                firebaseRepo.updateExpense(updatedExpense)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ExpensesActivity, "Dépense modifiée", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
        }

        val deleteButton = TextView(this).apply {
            text = "SUPPRIMER CETTE DÉPENSE"
            setPadding(0, 48, 0, 0)
            setTextColor(getColor(R.color.error))
            gravity = android.view.Gravity.CENTER
            setOnClickListener {
                AlertDialog.Builder(this@ExpensesActivity)
                    .setTitle("Suppression")
                    .setMessage("Voulez-vous vraiment supprimer cette dépense ?")
                    .setPositiveButton("Supprimer") { _, _ ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            if (expense.firestoreId != null) {
                                firebaseRepo.deleteExpense(expense.firestoreId)
                            }
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@ExpensesActivity, "Dépense supprimée", Toast.LENGTH_SHORT).show()
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
            R.id.nav_expenses -> {}
            R.id.nav_sales -> {
                val intent = Intent(this, SalesActivity::class.java)
                intent.putExtra("userIdString", userId)
                intent.putExtra("role", userRole)
                intent.putExtra("selectedBatchId", selectedBatchId)
                startActivity(intent)
            }
            R.id.nav_mortality -> {
                val intent = Intent(this, MortalityActivity::class.java)
                intent.putExtra("userIdString", userId)
                intent.putExtra("role", userRole)
                intent.putExtra("selectedBatchId", selectedBatchId)
                startActivity(intent)
            }
            R.id.nav_logout -> {
                firebaseRepo.logout()
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

    class ExpenseAdapter(private var currency: String, private val onItemClick: (Expense) -> Unit) : RecyclerView.Adapter<ExpenseAdapter.ViewHolder>() {
        private var items = listOf<Expense>()

        fun submitList(list: List<Expense>) {
            items = list
            notifyDataSetChanged()
        }
        
        fun updateCurrency(newCurrency: String) {
            this.currency = newCurrency
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemExpenseBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.bind(item, currency)
            holder.itemView.setOnClickListener { onItemClick(item) }
        }

        override fun getItemCount() = items.size

        class ViewHolder(private val binding: ItemExpenseBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(item: Expense, currency: String) {
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                binding.tvDate.text = sdf.format(Date(item.date))
                binding.tvCategory.text = item.category
                binding.tvDescription.text = item.description ?: ""
                
                if (item.category == "Aliment" && item.quantityKg != null && item.quantityKg > 0) {
                    binding.tvDescription.text = "${item.quantityKg.toInt()} kg - ${item.description ?: ""}"
                }
                
                binding.tvAmount.text = String.format(Locale.getDefault(), "%.0f %s", item.amount, currency)
            }
        }
    }
}
