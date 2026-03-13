package com.example.poulailler_copilot.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.poulailler_copilot.data.AppDatabase
import com.example.poulailler_copilot.data.EggSale
import com.example.poulailler_copilot.databinding.ActivitySalesBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SalesActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySalesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySalesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.root.findViewById(com.example.poulailler_copilot.R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Enregistrer une Vente"

        binding.btnSaveSale.setOnClickListener {
            saveSale()
        }
    }

    private fun saveSale() {
        val quantity = binding.etQuantity.text.toString().toIntOrNull()
        val unitPrice = binding.etUnitPrice.text.toString().toDoubleOrNull()
        val buyer = binding.etBuyer.text.toString()

        if (quantity == null || unitPrice == null) {
            Toast.makeText(this, "Veuillez remplir les champs obligatoires", Toast.LENGTH_SHORT).show()
            return
        }

        val totalPrice = quantity * unitPrice
        val sale = EggSale(
            date = System.currentTimeMillis(),
            quantity = quantity,
            pricePerUnit = unitPrice,
            totalPrice = totalPrice,
            buyer = if (buyer.isBlank()) null else buyer
        )

        lifecycleScope.launch(Dispatchers.IO) {
            AppDatabase.getInstance(this@SalesActivity).eggSaleDao().insert(sale)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@SalesActivity, "Vente enregistrée : $totalPrice $", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
