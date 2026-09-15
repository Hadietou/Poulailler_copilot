package com.hadietou.poulailler.ui

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hadietou.poulailler.data.VetVisit
import com.hadietou.poulailler.databinding.ActivityVetVisitBinding
import com.hadietou.poulailler.databinding.DialogAddVetVisitBinding
import com.hadietou.poulailler.databinding.ItemVetVisitBinding
import com.hadietou.poulailler.repository.FirebaseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Registre des consultations vétérinaires (Phase 3), accessible depuis le tableau de bord sanitaire. */
class VetVisitActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVetVisitBinding
    private val firebaseRepo = FirebaseRepository()
    private lateinit var adapter: VetVisitAdapter
    private var selectedBatchId: String? = null
    private val calendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVetVisitBinding.inflate(layoutInflater)
        setContentView(binding.root)

        selectedBatchId = intent.getStringExtra("selectedBatchId")
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = VetVisitAdapter()
        binding.rvVetVisits.layoutManager = LinearLayoutManager(this)
        binding.rvVetVisits.adapter = adapter

        observeVisits()
        binding.fabAddVetVisit.setOnClickListener { showAddVisitDialog() }
    }

    private fun observeVisits() {
        lifecycleScope.launch {
            firebaseRepo.getVetVisitsFlow().collectLatest { list ->
                val filtered = if (selectedBatchId != null) list.filter { it.batchId == selectedBatchId } else list
                withContext(Dispatchers.Main) {
                    binding.tvNoVisits.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
                    binding.rvVetVisits.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
                    adapter.submitList(filtered)
                }
            }
        }
    }

    private fun showAddVisitDialog() {
        val dialogBinding = DialogAddVetVisitBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this).setView(dialogBinding.root).create()
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        var visitDateMs = System.currentTimeMillis()
        var nextVisitMs: Long? = null
        dialogBinding.btnSelectVisitDate.text = "Date: ${sdf.format(Date(visitDateMs))}"

        dialogBinding.btnSelectVisitDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                val c = Calendar.getInstance(); c.set(y, m, d)
                visitDateMs = c.timeInMillis
                dialogBinding.btnSelectVisitDate.text = "Date: ${sdf.format(c.time)}"
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }
        dialogBinding.btnSelectNextVisitDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                val c = Calendar.getInstance(); c.set(y, m, d)
                nextVisitMs = c.timeInMillis
                dialogBinding.btnSelectNextVisitDate.text = "Prochaine visite: ${sdf.format(c.time)}"
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        dialogBinding.btnSaveVetVisit.setOnClickListener {
            val vetName = dialogBinding.etVetName.text?.toString()?.trim() ?: ""
            if (vetName.isEmpty()) {
                Toast.makeText(this, "Veuillez saisir le nom du vétérinaire", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val visit = VetVisit(
                date = visitDateMs,
                vetName = vetName,
                reason = dialogBinding.etVetReason.text?.toString()?.trim()?.ifEmpty { null },
                observations = dialogBinding.etVetObservations.text?.toString()?.trim()?.ifEmpty { null },
                diagnosis = dialogBinding.etVetDiagnosis.text?.toString()?.trim()?.ifEmpty { null },
                recommendations = dialogBinding.etVetRecommendations.text?.toString()?.trim()?.ifEmpty { null },
                vaccinationsDone = dialogBinding.etVetVaccinations.text?.toString()?.trim()?.ifEmpty { null },
                analysesRequested = dialogBinding.etVetAnalyses.text?.toString()?.trim()?.ifEmpty { null },
                nextVisitDate = nextVisitMs,
                batchId = selectedBatchId
            )
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    firebaseRepo.addVetVisit(visit)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@VetVisitActivity, "Visite enregistrée", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@VetVisitActivity, e.message ?: "Erreur", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        dialog.show()
    }

    class VetVisitAdapter : RecyclerView.Adapter<VetVisitAdapter.ViewHolder>() {
        private var items = listOf<VetVisit>()
        fun submitList(list: List<VetVisit>) { items = list; notifyDataSetChanged() }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemVetVisitBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])
        override fun getItemCount() = items.size

        class ViewHolder(private val binding: ItemVetVisitBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(item: VetVisit) {
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                binding.tvVetName.text = item.vetName
                binding.tvVetDate.text = sdf.format(Date(item.date))
                if (!item.reason.isNullOrEmpty()) {
                    binding.tvVetReason.text = "Motif : ${item.reason}"
                    binding.tvVetReason.visibility = View.VISIBLE
                } else binding.tvVetReason.visibility = View.GONE
                if (!item.diagnosis.isNullOrEmpty()) {
                    binding.tvVetDiagnosis.text = "Diagnostic : ${item.diagnosis}"
                    binding.tvVetDiagnosis.visibility = View.VISIBLE
                } else binding.tvVetDiagnosis.visibility = View.GONE
                if (item.nextVisitDate != null) {
                    binding.tvVetNextVisit.text = "📅 Prochaine visite : ${sdf.format(Date(item.nextVisitDate))}"
                    binding.tvVetNextVisit.visibility = View.VISIBLE
                } else binding.tvVetNextVisit.visibility = View.GONE
            }
        }
    }
}
