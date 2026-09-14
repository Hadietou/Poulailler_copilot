package com.hadietou.poulailler.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hadietou.poulailler.data.HealthReminderLog
import com.hadietou.poulailler.databinding.ItemHealthReminderLogBinding
import java.text.SimpleDateFormat
import java.util.*

/**
 * Affiche l'historique des rappels de santé marqués FAIT (voir [HealthReminderLog]) :
 * seul endroit de l'app où retrouver la liste et les dates de ces rappels.
 */
class HealthReminderLogAdapter : RecyclerView.Adapter<HealthReminderLogAdapter.ViewHolder>() {
    private var items = listOf<HealthReminderLog>()

    fun submitList(list: List<HealthReminderLog>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHealthReminderLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])

    override fun getItemCount() = items.size

    class ViewHolder(private val binding: ItemHealthReminderLogBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(log: HealthReminderLog) {
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            binding.tvLogTitle.text = log.title
            binding.tvLogDueDate.text = "Prévu le : ${sdf.format(Date(log.dueDate))}"
            binding.tvLogDoneDate.text = "Fait le\n${sdf.format(Date(log.doneDate))}"
        }
    }
}
