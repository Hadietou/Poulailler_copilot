package com.hadietou.poulailler.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.hadietou.poulailler.R
import com.hadietou.poulailler.data.HealthReminderLog
import com.hadietou.poulailler.databinding.ItemHealthReminderLogBinding
import java.text.SimpleDateFormat
import java.util.*

/**
 * Affiche l'historique des rappels de santé FAITS et IGNORÉS (voir [HealthReminderLog]) :
 * date prévue, date de l'action, statut et, pour un rappel cyclique, la prochaine échéance.
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
            val ctx = binding.root.context
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            binding.tvLogTitle.text = log.title
            binding.tvLogDueDate.text = "Prévu le : ${sdf.format(Date(log.dueDate))}"
            binding.tvLogDoneDate.text = "le ${sdf.format(Date(log.doneDate))}"

            if (log.nextDueDate != null) {
                binding.tvLogNextDate.text = "Prochain rappel : ${sdf.format(Date(log.nextDueDate))}"
                binding.tvLogNextDate.visibility = View.VISIBLE
            } else {
                binding.tvLogNextDate.visibility = View.GONE
            }

            val green = ContextCompat.getColor(ctx, R.color.emerald_soft)
            val greenBg = ContextCompat.getColor(ctx, R.color.emerald_container)
            val grey = ContextCompat.getColor(ctx, R.color.text_secondary)
            val greyBg = 0xFFEEEEEE.toInt()

            if (log.isIgnored) {
                binding.tvLogStatus.text = "IGNORÉ"
                binding.tvLogStatus.setTextColor(grey)
                binding.tvLogStatus.setBackgroundColor(greyBg)
                binding.tvLogDoneDate.setTextColor(grey)
                binding.ivLogIcon.setImageResource(R.drawable.ic_close)
                binding.ivLogIcon.setColorFilter(grey)
                binding.cardLog.strokeColor = greyBg
            } else {
                binding.tvLogStatus.text = "FAIT"
                binding.tvLogStatus.setTextColor(green)
                binding.tvLogStatus.setBackgroundColor(greenBg)
                binding.tvLogDoneDate.setTextColor(green)
                binding.ivLogIcon.setImageResource(R.drawable.ic_heart)
                binding.ivLogIcon.setColorFilter(green)
                binding.cardLog.strokeColor = greenBg
            }
        }
    }
}
