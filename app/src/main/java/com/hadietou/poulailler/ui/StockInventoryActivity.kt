package com.hadietou.poulailler.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hadietou.poulailler.R
import com.hadietou.poulailler.data.StockAdjustment
import com.hadietou.poulailler.databinding.ActivityStockInventoryBinding
import com.hadietou.poulailler.databinding.DialogStockInventoryBinding
import com.hadietou.poulailler.databinding.ItemStockAdjustmentBinding
import com.hadietou.poulailler.repository.FirebaseRepository
import com.hadietou.poulailler.util.StockType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Inventaire des stocks : l'éleveur compte l'aliment, les alvéoles ou les œufs, et l'écart avec le stock
 * affiché est enregistré avec un motif. Le stock calculé par l'application est corrigé de cet écart
 * (voir [com.hadietou.poulailler.util.StockAdjustments]) ; les achats, collectes et ventes ne sont pas modifiés.
 */
class StockInventoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStockInventoryBinding
    private val viewModel: DashboardViewModel by viewModels()
    private val firebaseRepo = FirebaseRepository()
    private lateinit var adapter: InventoryAdapter
    private var selectedBatchId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStockInventoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }
        selectedBatchId = intent.getStringExtra("selectedBatchId")

        adapter = InventoryAdapter { confirmDelete(it) }
        binding.rvInventories.layoutManager = LinearLayoutManager(this)
        binding.rvInventories.adapter = adapter

        binding.btnInventoryFeed.setOnClickListener { showInventoryDialog(StockType.FEED) }
        binding.btnInventoryTrays.setOnClickListener { showInventoryDialog(StockType.TRAYS) }
        binding.btnInventoryEggs.setOnClickListener { showInventoryDialog(StockType.EGGS) }

        observeStocks()
        viewModel.loadData()
    }

    private fun observeStocks() {
        // Même lot que le tableau de bord : les stocks aliment et œufs se calculent par lot.
        viewModel.allBatches.observe(this) { batches ->
            if (viewModel.selectedBatch.value == null) {
                val batch = batches.firstOrNull { it.firestoreId == selectedBatchId }
                    ?: batches.firstOrNull { it.status == "ACTIVE" }
                    ?: batches.firstOrNull()
                batch?.let { viewModel.selectBatch(it) }
            }
        }
        viewModel.selectedBatch.observe(this) { batch -> batch?.firestoreId?.let { selectedBatchId = it } }

        viewModel.currentStockKg.observe(this) { binding.tvFeedStock.text = StockType.FEED.format(it) }
        viewModel.eggTraysStock.observe(this) { binding.tvTraysStock.text = StockType.TRAYS.format((it ?: 0).toDouble()) }
        viewModel.totalRemaining.observe(this) { binding.tvEggsStock.text = StockType.EGGS.format(it.toDouble()) }

        viewModel.allStockAdjustments.observe(this) { list ->
            // Aliment et œufs : seulement les inventaires du lot affiché ; alvéoles : toute la ferme.
            val shown = list.filter { it.type == StockType.TRAYS.name || selectedBatchId == null || it.batchId == selectedBatchId }
            adapter.submit(shown)
            binding.tvNoInventory.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
            binding.rvInventories.visibility = if (shown.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    /** Stock actuellement affiché (déjà corrigé des inventaires précédents) pour ce type. */
    private fun currentStock(type: StockType): Double = when (type) {
        StockType.FEED -> viewModel.currentStockKg.value ?: 0.0
        StockType.TRAYS -> (viewModel.eggTraysStock.value ?: 0).toDouble()
        StockType.EGGS -> (viewModel.totalRemaining.value ?: 0).toDouble()
    }

    private fun showInventoryDialog(type: StockType) {
        val dialogBinding = DialogStockInventoryBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this).setView(dialogBinding.root).create()
        val current = currentStock(type)

        dialogBinding.tvInventoryTitle.text = "Inventaire · ${type.label}"
        dialogBinding.tvInventoryCurrent.text = "Stock dans l'application : ${type.format(current)}"
        dialogBinding.actvReason.setAdapter(ArrayAdapter(this, R.layout.item_dropdown_compact, type.reasons))

        val eggs = type == StockType.EGGS
        dialogBinding.tilCounted.visibility = if (eggs) View.GONE else View.VISIBLE
        dialogBinding.layoutCountedEggs.visibility = if (eggs) View.VISIBLE else View.GONE
        dialogBinding.tilCounted.hint = if (type == StockType.FEED) "Quantité comptée (kg)" else "Nombre d'alvéoles comptées"
        if (type == StockType.TRAYS) dialogBinding.etCounted.inputType = android.text.InputType.TYPE_CLASS_NUMBER

        // Quantité comptée saisie, ou null tant qu'elle est vide.
        fun counted(): Double? = if (eggs) {
            val trays = dialogBinding.etCountedTrays.text.toString().toIntOrNull()
            val extra = dialogBinding.etCountedExtra.text.toString().toIntOrNull()
            if (trays == null && extra == null) null else ((trays ?: 0) * 30 + (extra ?: 0)).toDouble()
        } else {
            dialogBinding.etCounted.text.toString().replace(',', '.').toDoubleOrNull()
        }

        fun refresh() {
            val value = counted()
            val extra = dialogBinding.etCountedExtra.text.toString().toIntOrNull() ?: 0
            dialogBinding.tilCountedExtra.error = if (eggs && extra >= 30) "Max 29 : 30 œufs = 1 plateau" else null
            dialogBinding.tvInventoryDifference.text = if (value == null) "Écart : saisissez la quantité comptée"
            else "Écart : ${type.formatDifference(value - current)}"
        }
        listOf(dialogBinding.etCounted, dialogBinding.etCountedTrays, dialogBinding.etCountedExtra).forEach { it.doAfterTextChanged { refresh() } }
        refresh()

        dialogBinding.btnSaveInventory.setOnClickListener {
            val value = counted()
            val reason = dialogBinding.actvReason.text.toString()
            val notes = dialogBinding.etNotes.text.toString().trim()
            val extra = dialogBinding.etCountedExtra.text.toString().toIntOrNull() ?: 0
            when {
                value == null || value < 0 -> Toast.makeText(this, "Saisissez la quantité comptée", Toast.LENGTH_SHORT).show()
                eggs && extra >= 30 -> Toast.makeText(this, "Œufs en plus : 29 au maximum", Toast.LENGTH_SHORT).show()
                abs(value - current) < 0.0001 -> {
                    Toast.makeText(this, "Aucun écart : le stock est déjà juste", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                reason.isEmpty() -> dialogBinding.tilReason.error = "Choisissez le motif de l'écart"
                reason == "Autre" && notes.isEmpty() -> dialogBinding.tilNotes.error = "Précisez le motif"
                else -> {
                    val adjustment = StockAdjustment(
                        type = type.name,
                        date = System.currentTimeMillis(),
                        theoretical = current,
                        counted = if (type == StockType.TRAYS) value.roundToInt().toDouble() else value,
                        reason = reason,
                        notes = notes.ifEmpty { null },
                        batchId = if (type == StockType.TRAYS) null else selectedBatchId
                    )
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            firebaseRepo.addStockAdjustment(adjustment)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@StockInventoryActivity, "Inventaire enregistré", Toast.LENGTH_SHORT).show()
                                dialog.dismiss()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@StockInventoryActivity, e.message ?: "Erreur", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    private fun confirmDelete(adjustment: StockAdjustment) {
        val id = adjustment.firestoreId ?: return
        AlertDialog.Builder(this)
            .setTitle("Annuler cet inventaire ?")
            .setMessage("Le stock reviendra à la valeur calculée sans cet inventaire (${StockType.fromKey(adjustment.type)?.formatDifference(-adjustment.difference) ?: ""}).")
            .setPositiveButton("Annuler l'inventaire") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        firebaseRepo.deleteStockAdjustment(id)
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@StockInventoryActivity, e.message ?: "Erreur", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Garder", null)
            .show()
    }

    class InventoryAdapter(private val onClick: (StockAdjustment) -> Unit) : RecyclerView.Adapter<InventoryAdapter.ViewHolder>() {
        private var items = listOf<StockAdjustment>()

        fun submit(list: List<StockAdjustment>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(ItemStockAdjustmentBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.bind(item)
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = items.size

        class ViewHolder(private val binding: ItemStockAdjustmentBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(item: StockAdjustment) {
                val context = binding.root.context
                val type = StockType.fromKey(item.type)
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                binding.tvAdjustmentType.text = type?.label ?: item.type
                binding.tvAdjustmentDate.text = sdf.format(Date(item.date))
                binding.tvAdjustmentValues.text = "Compté ${type?.format(item.counted)} · Application ${type?.format(item.theoretical)}"
                binding.tvAdjustmentReason.text = listOfNotNull(item.reason.ifEmpty { null }, item.notes).joinToString(" — ")

                val diff = item.difference
                binding.tvAdjustmentDifference.text = type?.formatDifference(diff) ?: diff.toString()
                binding.tvAdjustmentDifference.setTextColor(context.getColor(if (diff < 0) R.color.error else R.color.emerald_soft))
            }
        }
    }
}
