package com.example.poulailler_copilot.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.poulailler_copilot.data.AppDatabase
import com.example.poulailler_copilot.data.EggEntry
import com.example.poulailler_copilot.databinding.ActivityAgentBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class AgentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAgentBinding
    private val vm: AgentViewModel by viewModels()
    private var userId: Long = -1
    private var userRole: String = "AGENT"
    
    private var selectedDate = Calendar.getInstance()
    private val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAgentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userId = intent.getLongExtra("userId", -1)
        userRole = intent.getStringExtra("role") ?: "AGENT"

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

        if (userRole == "RESPONSABLE") {
            loadGlobalHistory()
        } else {
            vm.entries.observe(this) { list ->
                val items = list.map { e ->
                    val dateStr = dateFormatter.format(Date(e.date))
                    "Date: $dateStr - Récoltés: ${e.eggsCount} - Cassés: ${e.brokenEggsCount}\n${e.remarks ?: ""}"
                }
                binding.lvEntries.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
            }
            vm.loadEntries(userId)
        }

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
                if (userRole == "RESPONSABLE") loadGlobalHistory()
            }
        }
    }

    private fun loadGlobalHistory() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(this@AgentActivity)
            val entries = db.eggEntryDao().getAll()
            val displayList = entries.map { entry ->
                val user = db.userDao().getById(entry.userId)
                val username = user?.username ?: "Inconnu"
                val dateStr = dateFormatter.format(Date(entry.date))
                "$dateStr - $username: ${entry.eggsCount} œufs (Cassés: ${entry.brokenEggsCount})"
            }
            withContext(Dispatchers.Main) {
                binding.tvHistoryTitle.text = "Historique global des collectes"
                binding.lvEntries.adapter = ArrayAdapter(this@AgentActivity, android.R.layout.simple_list_item_1, displayList)
            }
        }
    }

    private fun updateDateButton() {
        binding.btnSelectDate.text = "Date: ${dateFormatter.format(selectedDate.time)}"
    }
}
