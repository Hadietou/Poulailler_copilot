package com.hadietou.poulailler.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hadietou.poulailler.data.HealthReminder
import com.hadietou.poulailler.databinding.ItemHealthReminderBinding
import java.text.SimpleDateFormat
import java.util.*

/**
 * Rappels de santé actifs, avec deux actions : FAIT (soin effectué) et IGNORER.
 * Les deux sont tracées avec leur date dans l'historique ; un rappel cyclique
 * revient dans tous les cas à la prochaine échéance.
 */
class HealthReminderAdapter(
    private val onDoneClick: (HealthReminder) -> Unit,
    private val onIgnoreClick: (HealthReminder) -> Unit
) : ListAdapter<HealthReminder, HealthReminderAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHealthReminderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemHealthReminderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(reminder: HealthReminder) {
            binding.tvReminderTitle.text = reminder.title

            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val cycle = if (reminder.recurring && (reminder.frequencyMonths ?: 0) > 0)
                " · tous les ${reminder.frequencyMonths} mois" else ""
            binding.tvReminderDate.text = "Prévu le : ${sdf.format(Date(reminder.dueDate))}$cycle"

            if (!reminder.description.isNullOrEmpty()) {
                binding.tvReminderDesc.text = reminder.description
                binding.tvReminderDesc.visibility = View.VISIBLE
            } else {
                binding.tvReminderDesc.visibility = View.GONE
            }

            binding.btnDone.setOnClickListener { onDoneClick(reminder) }
            binding.btnIgnore.setOnClickListener { onIgnoreClick(reminder) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<HealthReminder>() {
        override fun areItemsTheSame(oldItem: HealthReminder, newItem: HealthReminder) =
            (oldItem.firestoreId ?: oldItem.id.toString()) == (newItem.firestoreId ?: newItem.id.toString())
        override fun areContentsTheSame(oldItem: HealthReminder, newItem: HealthReminder) = oldItem == newItem
    }
}
