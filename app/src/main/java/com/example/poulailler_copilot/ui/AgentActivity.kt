package com.example.poulailler_copilot.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.poulailler_copilot.data.EggEntry
import com.example.poulailler_copilot.databinding.ActivityAgentBinding
import java.text.SimpleDateFormat
import java.util.*

class AgentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAgentBinding
    private val vm: AgentViewModel by viewModels()
    private var userId: Long = -1
    private var entries: List<EggEntry> = emptyList()
    
    private var selectedDate = Calendar.getInstance()
    private val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAgentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userId = intent.getLongExtra("userId", -1)
        if (userId == -1L) {
            Toast.makeText(this, "Erreur utilisateur", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        updateDateButton()

        binding.btnSelectDate.setOnClickListener {
            val dpd = DatePickerDialog(this, { _, year, month, dayOfMonth ->
                selectedDate.set(year, month, dayOfMonth)
                updateDateButton()
            }, selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH), selectedDate.get(Calendar.DAY_OF_MONTH))
            dpd.show()
        }

        vm.entries.observe(this) { list ->
            entries = list
            val items = list.map { e ->
                val dateStr = dateFormatter.format(Date(e.date))
                "Date: $dateStr - Récoltés: ${e.eggsCount} - Cassés: ${e.brokenEggsCount}\n${e.remarks ?: ""}"
            }
            binding.lvEntries.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
        }

        vm.loadEntries(userId)

        binding.btnAddEntry.setOnClickListener {
            val eggs = binding.etEggsCount.text.toString().toIntOrNull()
            val broken = binding.etBrokenEggsCount.text.toString().toIntOrNull() ?: 0
            val remarks = binding.etRemarks.text.toString()

            if (eggs == null) {
                Toast.makeText(this, "Nombre d'œufs récoltés invalide", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            vm.addEntry(userId, selectedDate.timeInMillis, eggs, broken, if (remarks.isBlank()) null else remarks) {
                Toast.makeText(this, "Saisie enregistrée", Toast.LENGTH_SHORT).show()
                binding.etEggsCount.text.clear()
                binding.etBrokenEggsCount.text.clear()
                binding.etRemarks.text.clear()
            }
        }
    }

    private fun updateDateButton() {
        binding.btnSelectDate.text = "Date: ${dateFormatter.format(selectedDate.time)}"
    }
}
