package com.hadietou.poulailler.ui

import android.app.AlertDialog
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
import com.hadietou.poulailler.data.BiosecurityTask
import com.hadietou.poulailler.databinding.ActivityBiosecurityBinding
import com.hadietou.poulailler.databinding.DialogAddBiosecurityTaskBinding
import com.hadietou.poulailler.databinding.ItemBiosecurityTaskBinding
import com.hadietou.poulailler.repository.FirebaseRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Check-lists de biosécurité récurrentes (Phase 3), rattachées à la ferme entière. */
class BiosecurityActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBiosecurityBinding
    private val firebaseRepo = FirebaseRepository()
    private lateinit var adapter: BiosecurityAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBiosecurityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = BiosecurityAdapter { task -> markDone(task) }
        binding.rvTasks.layoutManager = LinearLayoutManager(this)
        binding.rvTasks.adapter = adapter

        observeTasks()
        binding.fabAddTask.setOnClickListener { showAddDialog() }
    }

    private fun observeTasks() {
        lifecycleScope.launch {
            firebaseRepo.getBiosecurityTasksFlow().collectLatest { list ->
                val sorted = list.sortedWith(compareByDescending<BiosecurityTask> { it.isOverdue }.thenBy { it.task })
                withContext(Dispatchers.Main) {
                    binding.tvNoTasks.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE
                    binding.rvTasks.visibility = if (sorted.isEmpty()) View.GONE else View.VISIBLE
                    adapter.submitList(sorted)
                }
            }
        }
    }

    private fun markDone(task: BiosecurityTask) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val uid = FirebaseAuth.getInstance().currentUser?.uid
                val username = uid?.let { firebaseRepo.getUserProfile(it)?.username }
                firebaseRepo.updateBiosecurityTask(task.copy(lastDoneDate = System.currentTimeMillis(), doneBy = username))
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@BiosecurityActivity, "Tâche marquée comme faite", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@BiosecurityActivity, e.message ?: "Erreur", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showAddDialog() {
        val dialogBinding = DialogAddBiosecurityTaskBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this).setView(dialogBinding.root).create()

        dialogBinding.actvTaskName.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, BiosecurityTask.COMMON_TASKS))
        dialogBinding.actvTaskFrequency.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, BiosecurityTask.FREQUENCY_LABELS.values.toList()))
        dialogBinding.actvTaskFrequency.setText(BiosecurityTask.FREQUENCY_LABELS.getValue("HEBDOMADAIRE"), false)

        dialogBinding.btnSaveTask.setOnClickListener {
            val taskName = dialogBinding.actvTaskName.text?.toString()?.trim() ?: ""
            if (taskName.isEmpty()) {
                Toast.makeText(this, "Veuillez saisir ou choisir une tâche", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val freqLabel = dialogBinding.actvTaskFrequency.text?.toString() ?: ""
            val frequency = BiosecurityTask.FREQUENCY_LABELS.entries.find { it.value == freqLabel }?.key ?: "HEBDOMADAIRE"
            val task = BiosecurityTask(task = taskName, frequency = frequency)
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    firebaseRepo.addBiosecurityTask(task)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@BiosecurityActivity, "Tâche ajoutée", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@BiosecurityActivity, e.message ?: "Erreur", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        dialog.show()
    }

    class BiosecurityAdapter(private val onMarkDone: (BiosecurityTask) -> Unit) : RecyclerView.Adapter<BiosecurityAdapter.ViewHolder>() {
        private var items = listOf<BiosecurityTask>()
        fun submitList(list: List<BiosecurityTask>) { items = list; notifyDataSetChanged() }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemBiosecurityTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.bind(item)
            holder.binding.btnMarkDone.setOnClickListener { onMarkDone(item) }
        }
        override fun getItemCount() = items.size

        class ViewHolder(val binding: ItemBiosecurityTaskBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(item: BiosecurityTask) {
                val context = binding.root.context
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                binding.tvTaskName.text = item.task
                val freqLabel = BiosecurityTask.FREQUENCY_LABELS[item.frequency] ?: item.frequency
                val lastDone = item.lastDoneDate?.let { "Fait le ${sdf.format(Date(it))}" } ?: "Jamais fait"
                binding.tvTaskFrequency.text = "$freqLabel · $lastDone"

                val (label, colorRes) = if (item.isOverdue) {
                    "EN RETARD" to com.hadietou.poulailler.R.color.error
                } else {
                    "À JOUR" to com.hadietou.poulailler.R.color.emerald_soft
                }
                binding.tvTaskStatus.text = label
                val color = context.getColor(colorRes)
                binding.tvTaskStatus.setTextColor(color)
                (binding.tvTaskStatus.background.mutate() as? android.graphics.drawable.GradientDrawable)?.setColor(
                    android.graphics.Color.argb(30, android.graphics.Color.red(color), android.graphics.Color.green(color), android.graphics.Color.blue(color))
                )
            }
        }
    }
}
