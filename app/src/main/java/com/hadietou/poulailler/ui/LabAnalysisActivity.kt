package com.hadietou.poulailler.ui

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hadietou.poulailler.data.LabAnalysis
import com.hadietou.poulailler.databinding.ActivityLabAnalysisBinding
import com.hadietou.poulailler.databinding.DialogAddLabAnalysisBinding
import com.hadietou.poulailler.databinding.ItemLabAnalysisBinding
import com.hadietou.poulailler.repository.FirebaseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Registre des analyses et examens (Phase 3), accessible depuis le tableau de bord sanitaire. */
class LabAnalysisActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLabAnalysisBinding
    private val firebaseRepo = FirebaseRepository()
    private lateinit var adapter: LabAnalysisAdapter
    private var selectedBatchId: String? = null
    private val calendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLabAnalysisBinding.inflate(layoutInflater)
        setContentView(binding.root)

        selectedBatchId = intent.getStringExtra("selectedBatchId")
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = LabAnalysisAdapter()
        binding.rvAnalyses.layoutManager = LinearLayoutManager(this)
        binding.rvAnalyses.adapter = adapter

        observeAnalyses()
        binding.fabAddAnalysis.setOnClickListener { showAddDialog() }
    }

    private fun observeAnalyses() {
        lifecycleScope.launch {
            firebaseRepo.getLabAnalysesFlow().collectLatest { list ->
                val filtered = if (selectedBatchId != null) list.filter { it.batchId == selectedBatchId } else list
                withContext(Dispatchers.Main) {
                    binding.tvNoAnalyses.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
                    binding.rvAnalyses.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
                    adapter.submitList(filtered)
                }
            }
        }
    }

    private fun showAddDialog() {
        val dialogBinding = DialogAddLabAnalysisBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this).setView(dialogBinding.root).create()
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        dialogBinding.actvAnalysisType.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, LabAnalysis.TYPE_LABELS.values.toList()))
        dialogBinding.actvAnalysisType.setText(LabAnalysis.TYPE_LABELS.getValue("LABORATOIRE"), false)

        var dateMs = System.currentTimeMillis()
        dialogBinding.btnSelectAnalysisDate.text = "Date: ${sdf.format(Date(dateMs))}"
        dialogBinding.btnSelectAnalysisDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                val c = Calendar.getInstance(); c.set(y, m, d)
                dateMs = c.timeInMillis
                dialogBinding.btnSelectAnalysisDate.text = "Date: ${sdf.format(c.time)}"
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        dialogBinding.btnSaveAnalysis.setOnClickListener {
            val typeLabel = dialogBinding.actvAnalysisType.text?.toString() ?: ""
            val type = LabAnalysis.TYPE_LABELS.entries.find { it.value == typeLabel }?.key ?: "AUTRE"
            val analysis = LabAnalysis(
                type = type,
                date = dateMs,
                resultSummary = dialogBinding.etAnalysisResult.text?.toString()?.trim()?.ifEmpty { null },
                notes = dialogBinding.etAnalysisNotes.text?.toString()?.trim()?.ifEmpty { null },
                batchId = selectedBatchId
            )
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    firebaseRepo.addLabAnalysis(analysis)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@LabAnalysisActivity, "Analyse enregistrée", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@LabAnalysisActivity, e.message ?: "Erreur", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        dialog.show()
    }

    class LabAnalysisAdapter : RecyclerView.Adapter<LabAnalysisAdapter.ViewHolder>() {
        private var items = listOf<LabAnalysis>()
        fun submitList(list: List<LabAnalysis>) { items = list; notifyDataSetChanged() }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemLabAnalysisBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])
        override fun getItemCount() = items.size

        class ViewHolder(private val binding: ItemLabAnalysisBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(item: LabAnalysis) {
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                binding.tvAnalysisType.text = LabAnalysis.TYPE_LABELS[item.type] ?: item.type
                binding.tvAnalysisDate.text = sdf.format(Date(item.date))
                if (!item.resultSummary.isNullOrEmpty()) {
                    binding.tvAnalysisResult.text = "Résultat : ${item.resultSummary}"
                    binding.tvAnalysisResult.visibility = View.VISIBLE
                } else binding.tvAnalysisResult.visibility = View.GONE
            }
        }
    }
}
