package com.hadietou.poulailler.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.hadietou.poulailler.R
import com.hadietou.poulailler.data.Batch
import com.hadietou.poulailler.databinding.ItemBatchBinding
import java.text.SimpleDateFormat
import java.util.*

class BatchAdapter(private val onDeleteClick: (Batch) -> Unit) : RecyclerView.Adapter<BatchAdapter.BatchViewHolder>() {

    private var batches = listOf<Batch>()
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    fun submitList(newList: List<Batch>) {
        batches = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BatchViewHolder {
        val binding = ItemBatchBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BatchViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BatchViewHolder, position: Int) {
        holder.bind(batches[position])
    }

    override fun getItemCount(): Int = batches.size

    inner class BatchViewHolder(private val binding: ItemBatchBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(batch: Batch) {
            binding.tvBatchName.text = batch.name
            binding.tvBatchStatus.text = batch.status
            
            // Gestion du type de lot
            binding.tvBatchType.text = batch.typeLot
            if (batch.typeLot == "CHAIR") {
                binding.tvBatchType.setBackgroundResource(R.drawable.table_border)
                binding.tvBatchType.backgroundTintList = ContextCompat.getColorStateList(binding.root.context, R.color.earthy_container)
                binding.tvBatchType.setTextColor(ContextCompat.getColor(binding.root.context, R.color.earthy_orange))
                binding.tvBatchInfo.text = "${batch.hensCount} sujets - Race: ${batch.henBreed}"
            } else {
                binding.tvBatchType.setBackgroundResource(R.drawable.table_border)
                binding.tvBatchType.backgroundTintList = ContextCompat.getColorStateList(binding.root.context, R.color.accent_blue_container)
                binding.tvBatchType.setTextColor(ContextCompat.getColor(binding.root.context, R.color.accent_blue))
                binding.tvBatchInfo.text = "${batch.hensCount} poules - Race: ${batch.henBreed}"
            }

            binding.tvBatchArrivalDate.text = "Arrivée le ${dateFormat.format(Date(batch.arrivalDate))}"
            binding.tvBatchBirthDate.text = "Éclosion le ${dateFormat.format(Date(batch.chickBirthDate))}"
            
            binding.root.setOnLongClickListener {
                onDeleteClick(batch)
                true
            }
        }
    }
}
