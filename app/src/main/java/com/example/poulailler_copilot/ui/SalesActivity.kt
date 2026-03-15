package com.example.poulailler_copilot.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.poulailler_copilot.data.AppDatabase
import com.example.poulailler_copilot.data.EggSale
import com.example.poulailler_copilot.databinding.ActivitySalesBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class SalesActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySalesBinding
    private var selectedDate: Calendar = Calendar.getInstance()
    private var userId: Long = -1
    private var userRole: String = "AGENT"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySalesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userId = intent.getLongExtra("userId", -1)
        userRole = intent.getStringExtra("role") ?: "AGENT"

        updateDateDisplay()
        loadSalesHistory()

        binding.etSaleDate.setOnClickListener {
            showDatePicker()
        }

        binding.btnSaveSale.setOnClickListener {
            saveSale()
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

    private fun loadSalesHistory() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(this@SalesActivity)
            val salesFlow = if (userRole == "RESPONSABLE") {
                db.eggSaleDao().getAll()
            } else {
                db.eggSaleDao().getByUser(userId)
            }
            
            val sales = salesFlow.first()
            
            val displayList = sales.map { sale ->
                val user = db.userDao().getById(sale.userId)
                val sellerName = user?.username ?: "Inconnu"
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val buyerInfo = if (!sale.buyer.isNullOrBlank()) " Client: ${sale.buyer}" else ""
                val phoneInfo = if (!sale.phoneNumber.isNullOrBlank()) " (Tel: ${sale.phoneNumber})" else ""
                
                "${sdf.format(Date(sale.date))} - $sellerName: ${sale.quantity} œufs, Total: ${sale.totalPrice} \$$buyerInfo$phoneInfo"
            }

            withContext(Dispatchers.Main) {
                binding.tvHistoryTitle.text = if (userRole == "RESPONSABLE") "Historique global des ventes" else "Mes ventes"
                binding.lvSales.adapter = ArrayAdapter(this@SalesActivity, android.R.layout.simple_list_item_1, displayList)
            }
        }
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
            userId = userId,
            date = selectedDate.timeInMillis,
            quantity = quantity,
            pricePerUnit = unitPrice,
            totalPrice = totalPrice,
            buyer = if (buyer.isBlank()) null else buyer,
            phoneNumber = if (phone.isBlank()) null else phone
        )

        lifecycleScope.launch(Dispatchers.IO) {
            AppDatabase.getInstance(this@SalesActivity).eggSaleDao().insert(sale)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@SalesActivity, "Vente enregistrée !", Toast.LENGTH_LONG).show()
                loadSalesHistory()
                binding.etQuantity.text?.clear()
                binding.etUnitPrice.text?.clear()
                binding.etBuyer.text?.clear()
                binding.etPhoneNumber.text?.clear()
            }
        }
    }
}
