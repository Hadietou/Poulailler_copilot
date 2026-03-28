package com.example.poulailler_copilot.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.poulailler_copilot.data.AppDatabase
import com.example.poulailler_copilot.data.EggSale
import com.example.poulailler_copilot.databinding.ActivitySalesHistoryBinding
import com.example.poulailler_copilot.databinding.ItemSaleBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class SalesHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySalesHistoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySalesHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val adapter = SalesAdapter()
        binding.rvSalesHistory.layoutManager = LinearLayoutManager(this)
        binding.rvSalesHistory.adapter = adapter

        lifecycleScope.launch {
            val db = AppDatabase.getInstance(this@SalesHistoryActivity)
            db.eggSaleDao().getAll().collectLatest { list ->
                adapter.submitList(list)
            }
        }

        binding.btnClose.setOnClickListener { finish() }
    }

    class SalesAdapter : RecyclerView.Adapter<SalesAdapter.ViewHolder>() {
        private var items = listOf<EggSale>()

        fun submitList(list: List<EggSale>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemSaleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount() = items.size

        class ViewHolder(private val binding: ItemSaleBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(item: EggSale) {
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                binding.tvDate.text = sdf.format(Date(item.date))
                binding.tvClient.text = item.buyer?.takeIf { it.isNotEmpty() } ?: "Client Anonyme"
                binding.tvDetails.text = "${item.quantity} œufs x ${String.format("%.2f", item.pricePerUnit)} $"
                binding.tvTotal.text = String.format("%.2f $", item.totalPrice)
            }
        }
    }
}
