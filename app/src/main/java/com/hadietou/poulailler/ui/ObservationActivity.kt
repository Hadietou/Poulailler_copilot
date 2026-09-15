package com.hadietou.poulailler.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hadietou.poulailler.R
import com.hadietou.poulailler.data.HealthObservation
import com.hadietou.poulailler.databinding.ActivityObservationBinding
import com.hadietou.poulailler.databinding.DialogAddObservationBinding
import com.hadietou.poulailler.databinding.ItemObservationBinding
import com.hadietou.poulailler.repository.FirebaseRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Check-list sanitaire quotidienne (Phase 3). Accessible uniquement depuis le tableau de bord
 * sanitaire (pas de tiroir ici, page volontairement légère — voir la note d'architecture :
 * seules Suivi Sanitaire, Traitements et Symptômes restent dans le tiroir global).
 */
class ObservationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityObservationBinding
    private val firebaseRepo = FirebaseRepository()
    private lateinit var adapter: ObservationAdapter
    private var selectedBatchId: String? = null
    private var allObservations: List<HealthObservation> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityObservationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        selectedBatchId = intent.getStringExtra("selectedBatchId")
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = ObservationAdapter()
        binding.rvObservations.layoutManager = LinearLayoutManager(this)
        binding.rvObservations.adapter = adapter

        observeObservations()

        binding.btnQuickRas.setOnClickListener { saveObservation(HealthObservation(date = System.currentTimeMillis(), batchId = selectedBatchId)) }
        binding.btnReportAnomaly.setOnClickListener { showAddObservationDialog() }
    }

    private fun observeObservations() {
        lifecycleScope.launch {
            firebaseRepo.getHealthObservationsFlow().collectLatest { list ->
                allObservations = if (selectedBatchId != null) list.filter { it.batchId == selectedBatchId } else list
                withContext(Dispatchers.Main) {
                    binding.tvNoObservations.visibility = if (allObservations.isEmpty()) View.VISIBLE else View.GONE
                    binding.rvObservations.visibility = if (allObservations.isEmpty()) View.GONE else View.VISIBLE
                    adapter.submitList(allObservations)
                }
            }
        }
    }

    private fun saveObservation(observation: HealthObservation) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val uid = FirebaseAuth.getInstance().currentUser?.uid
                val recordedBy = uid?.let { firebaseRepo.getUserProfile(it)?.username }
                firebaseRepo.addHealthObservation(observation.copy(recordedBy = recordedBy))
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ObservationActivity, "Observation enregistrée", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ObservationActivity, e.message ?: "Erreur", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showAddObservationDialog() {
        val dialogBinding = DialogAddObservationBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this).setView(dialogBinding.root).create()

        dialogBinding.actvBehavior.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, HealthObservation.behaviorLabels.values.toList()))
        dialogBinding.actvFeed.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, HealthObservation.feedLabels.values.toList()))
        dialogBinding.actvWater.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, HealthObservation.waterLabels.values.toList()))
        dialogBinding.actvRespiration.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, HealthObservation.respirationLabels.values.toList()))
        dialogBinding.actvDroppings.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, HealthObservation.droppingsLabels.values.toList()))
        dialogBinding.actvAppearance.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, HealthObservation.appearanceLabels.values.toList()))

        dialogBinding.actvBehavior.setText(HealthObservation.behaviorLabels.getValue("NORMAL"), false)
        dialogBinding.actvFeed.setText(HealthObservation.feedLabels.getValue("NORMAL"), false)
        dialogBinding.actvWater.setText(HealthObservation.waterLabels.getValue("NORMAL"), false)
        dialogBinding.actvRespiration.setText(HealthObservation.respirationLabels.getValue("NORMAL"), false)
        dialogBinding.actvDroppings.setText(HealthObservation.droppingsLabels.getValue("NORMAL"), false)
        dialogBinding.actvAppearance.setText(HealthObservation.appearanceLabels.getValue("NORMAL"), false)

        dialogBinding.btnSaveObservation.setOnClickListener {
            fun keyFor(map: Map<String, String>, label: String) = map.entries.find { it.value == label }?.key ?: map.keys.first()
            val observation = HealthObservation(
                date = System.currentTimeMillis(),
                behavior = keyFor(HealthObservation.behaviorLabels, dialogBinding.actvBehavior.text.toString()),
                feed = keyFor(HealthObservation.feedLabels, dialogBinding.actvFeed.text.toString()),
                water = keyFor(HealthObservation.waterLabels, dialogBinding.actvWater.text.toString()),
                respiration = keyFor(HealthObservation.respirationLabels, dialogBinding.actvRespiration.text.toString()),
                droppings = keyFor(HealthObservation.droppingsLabels, dialogBinding.actvDroppings.text.toString()),
                appearance = keyFor(HealthObservation.appearanceLabels, dialogBinding.actvAppearance.text.toString()),
                notes = dialogBinding.etObservationNotes.text?.toString()?.trim()?.ifEmpty { null },
                batchId = selectedBatchId
            )
            saveObservation(observation)
            dialog.dismiss()
        }
        dialog.show()
    }

    class ObservationAdapter : RecyclerView.Adapter<ObservationAdapter.ViewHolder>() {
        private var items = listOf<HealthObservation>()

        fun submitList(list: List<HealthObservation>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemObservationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])
        override fun getItemCount() = items.size

        class ViewHolder(private val binding: ItemObservationBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(item: HealthObservation) {
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                binding.tvObservationDate.text = sdf.format(Date(item.date))
                if (item.anomalyDetected) {
                    binding.tvObservationIcon.text = "⚠️"
                    binding.tvObservationSummary.text = item.anomalyFields.joinToString(" · ") { (k, v) -> "$k : $v" }
                } else {
                    binding.tvObservationIcon.text = "✅"
                    binding.tvObservationSummary.text = "Rien à signaler"
                }
            }
        }
    }
}
