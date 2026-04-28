package com.hadietou.poulailler.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hadietou.poulailler.data.AppDatabase
import com.hadietou.poulailler.data.Mortality
import com.hadietou.poulailler.databinding.ActivityMortalityHistoryBinding
import com.hadietou.poulailler.databinding.ItemMortalityBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MortalityHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMortalityHistoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMortalityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val adapter = MortalityAdapter()
        binding.rvMortalityHistory.layoutManager = LinearLayoutManager(this)
        binding.rvMortalityHistory.adapter = adapter

        lifecycleScope.launch {
            val db = AppDatabase.getInstance(this@MortalityHistoryActivity)
            db.mortalityDao().getAllMortality().collectLatest { list ->
                adapter.submitList(list)
            }
        }

        binding.btnClose.setOnClickListener { finish() }
    }

    class MortalityAdapter : RecyclerView.Adapter<MortalityAdapter.ViewHolder>() {
        private var items = listOf<Mortality>()

        fun submitList(list: List<Mortality>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemMortalityBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount() = items.size

        class ViewHolder(private val binding: ItemMortalityBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(item: Mortality) {
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                binding.tvDate.text = sdf.format(Date(item.date))
                binding.tvCount.text = "${item.count} Poules"
            }
        }
    }
}
