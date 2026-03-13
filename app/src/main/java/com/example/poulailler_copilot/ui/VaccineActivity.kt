package com.example.poulailler_copilot.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.poulailler_copilot.data.AppDatabase
import com.example.poulailler_copilot.data.VaccineEntry
import com.example.poulailler_copilot.databinding.ActivityVaccineBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class VaccineActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVaccineBinding
    private var selectedDate = Calendar.getInstance()
    private val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVaccineBinding.inflate(layoutInflater)
        setContentView(binding.root)

        updateDateButton()
        loadProphylaxis()
        loadHistory()

        binding.btnSelectDate.setOnClickListener {
            DatePickerDialog(this, { _, year, month, dayOfMonth ->
                selectedDate.set(year, month, dayOfMonth)
                updateDateButton()
            }, selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH), selectedDate.get(Calendar.DAY_OF_MONTH)).show()
        }

        binding.btnSaveVaccine.setOnClickListener {
            val name = binding.etVaccineName.text.toString().trim()
            val remarks = binding.etRemarks.text.toString().trim()

            if (name.isEmpty()) {
                Toast.makeText(this, "Entrez le nom du produit", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            saveEntry(name, selectedDate.timeInMillis, if (remarks.isEmpty()) null else remarks)
        }
    }

    private fun updateDateButton() {
        binding.btnSelectDate.text = "Date: ${dateFormatter.format(selectedDate.time)}"
    }

    private fun loadProphylaxis() {
        // Guide simplifié pour pondeuses (Sénégal/Mauritanie)
        val guide = """
            GUIDE DE PROPHYLAXIE (PONDEUSES) :
            - J1 : Marek (Couvoir)
            - J7 : Newcastle (HB1) + Bronchite Infectieuse
            - J14 : Gumboro (1ère dose)
            - J21 : Gumboro (2ème dose)
            - J28 : Newcastle (Lasota) + BI
            - Semaine 8 : Variole aviaire
            - Semaine 12 : Typhose
            - Semaine 16 : Newcastle + Bronchite (Rappel avant ponte)
            - Tous les mois : Rappel Newcastle (Lasota ou huileux)
            - Déparasitage : Toutes les 8-12 semaines.
        """.trimIndent()
        binding.tvProphylaxisInfo.text = guide
    }

    private fun saveEntry(name: String, date: Long, remarks: String?) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(applicationContext)
            db.vaccineEntryDao().insert(VaccineEntry(name = name, date = date, remarks = remarks))
            withContext(Dispatchers.Main) {
                Toast.makeText(this@VaccineActivity, "Enregistré", Toast.LENGTH_SHORT).show()
                binding.etVaccineName.text.clear()
                binding.etRemarks.text.clear()
                loadHistory()
            }
        }
    }

    private fun loadHistory() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(applicationContext)
            val list = db.vaccineEntryDao().getAll()
            withContext(Dispatchers.Main) {
                val items = list.map { "${dateFormatter.format(Date(it.date))} - ${it.name} ${it.remarks ?: ""}" }
                binding.lvVaccines.adapter = ArrayAdapter(this@VaccineActivity, android.R.layout.simple_list_item_1, items)
            }
        }
    }
}
