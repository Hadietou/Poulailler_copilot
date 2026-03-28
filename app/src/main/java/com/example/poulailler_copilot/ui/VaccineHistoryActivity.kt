package com.example.poulailler_copilot.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.poulailler_copilot.data.AppDatabase
import com.example.poulailler_copilot.data.VaccineEntry
import com.example.poulailler_copilot.databinding.ActivityVaccineHistoryBinding
import com.example.poulailler_copilot.databinding.ItemVaccineBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class VaccineHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVaccineHistoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVaccineHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val adapter = VaccineAdapter()
        binding.rvVaccineHistory.layoutManager = LinearLayoutManager(this)
        binding.rvVaccineHistory.adapter = adapter

        lifecycleScope.launch {
            val db = AppDatabase.getInstance(this@VaccineHistoryActivity)
            db.vaccineEntryDao().getAllFlow().collectLatest { list ->
                adapter.submitList(list)
            }
        }

        binding.btnClose.setOnClickListener { finish() }
    }

    class VaccineAdapter : RecyclerView.Adapter<VaccineAdapter.ViewHolder>() {
        private var items = listOf<VaccineEntry>()

        fun submitList(list: List<VaccineEntry>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemVaccineBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount() = items.size

        class ViewHolder(private val binding: ItemVaccineBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(item: VaccineEntry) {
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                binding.tvDate.text = sdf.format(Date(item.date))
                binding.tvVaccineName.text = item.name
                binding.tvRemarks.text = item.remarks
            }
        }
    }
}
