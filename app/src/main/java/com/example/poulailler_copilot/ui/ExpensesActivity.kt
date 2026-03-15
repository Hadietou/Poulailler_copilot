package com.example.poulailler_copilot.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.poulailler_copilot.data.AppDatabase
import com.example.poulailler_copilot.data.Expense
import com.example.poulailler_copilot.databinding.ActivityExpensesBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class ExpensesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityExpensesBinding
    private lateinit var db: AppDatabase
    private var selectedDate: Calendar = Calendar.getInstance()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExpensesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        db = AppDatabase.getInstance(this)

        updateDateDisplay()
        setupCategorySpinner()
        loadExpensesHistory()
        
        binding.etExpenseDate.setOnClickListener {
            showDatePicker()
        }

        binding.btnSaveExpense.setOnClickListener {
            saveExpense()
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
        binding.etExpenseDate.setText(sdf.format(selectedDate.time))
    }

    private fun setupCategorySpinner() {
        val categories = arrayOf("Aliment", "Produit vétérinaire", "Salaire gardien", "Autres dépenses")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categories)
        binding.autoCompleteCategory.setAdapter(adapter)

        binding.autoCompleteCategory.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val selected = categories[position]
            binding.tilQuantityKg.visibility = if (selected == "Aliment") View.VISIBLE else View.GONE
        }
    }

    private fun loadExpensesHistory() {
        lifecycleScope.launch(Dispatchers.IO) {
            val expenses = db.expenseDao().getAll()
            withContext(Dispatchers.Main) {
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val displayList = expenses.map { 
                    val qty = if (it.quantityKg != null) " (${it.quantityKg} kg)" else ""
                    "${sdf.format(Date(it.date))} - ${it.category}$qty: ${it.amount} $"
                }
                binding.lvExpenses.adapter = ArrayAdapter(this@ExpensesActivity, android.R.layout.simple_list_item_1, displayList)
            }
        }
    }

    private fun saveExpense() {
        val category = binding.autoCompleteCategory.text.toString()
        val amountStr = binding.etAmount.text.toString()
        val description = binding.etDescription.text.toString()
        val qtyStr = binding.etQuantityKg.text.toString()

        if (category.isEmpty() || amountStr.isEmpty()) {
            Toast.makeText(this, "Veuillez remplir les champs obligatoires", Toast.LENGTH_SHORT).show()
            return
        }

        val amount = amountStr.toDoubleOrNull() ?: 0.0
        val qtyKg = if (category == "Aliment") qtyStr.toDoubleOrNull() else null

        val expense = Expense(
            date = selectedDate.timeInMillis,
            category = category,
            amount = amount,
            quantityKg = qtyKg,
            description = description
        )

        lifecycleScope.launch(Dispatchers.IO) {
            db.expenseDao().insert(expense)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@ExpensesActivity, "Dépense enregistrée !", Toast.LENGTH_SHORT).show()
                loadExpensesHistory()
                binding.etAmount.text?.clear()
                binding.etDescription.text?.clear()
                binding.etQuantityKg.text?.clear()
                binding.autoCompleteCategory.text.clear()
            }
        }
    }
}
