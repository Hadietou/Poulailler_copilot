package com.example.poulailler_copilot.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.poulailler_copilot.data.Batch
import com.example.poulailler_copilot.databinding.ItemBatchBinding
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
            binding.tvBatchInfo.text = "${batch.hensCount} poules - Race: ${batch.henBreed}"
            binding.tvBatchArrivalDate.text = "Arrivée le ${dateFormat.format(Date(batch.arrivalDate))}"
            binding.tvBatchBirthDate.text = "Éclosion le ${dateFormat.format(Date(batch.chickBirthDate))}"
            
            binding.root.setOnLongClickListener {
                onDeleteClick(batch)
                true
            }
        }
    }
}
