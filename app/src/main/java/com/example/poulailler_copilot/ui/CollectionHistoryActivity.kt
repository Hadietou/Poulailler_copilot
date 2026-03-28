package com.example.poulailler_copilot.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.poulailler_copilot.data.AppDatabase
import com.example.poulailler_copilot.data.EggEntry
import com.example.poulailler_copilot.databinding.ActivityCollectionHistoryBinding
import com.example.poulailler_copilot.databinding.ItemCollectionBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class CollectionHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCollectionHistoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCollectionHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val adapter = CollectionAdapter()
        binding.rvCollectionHistory.layoutManager = LinearLayoutManager(this)
        binding.rvCollectionHistory.adapter = adapter

        lifecycleScope.launch {
            val db = AppDatabase.getInstance(this@CollectionHistoryActivity)
            db.eggEntryDao().getAllFlow().collectLatest { list ->
                adapter.submitList(list)
            }
        }

        binding.btnClose.setOnClickListener { finish() }
    }

    class CollectionAdapter : RecyclerView.Adapter<CollectionAdapter.ViewHolder>() {
        private var items = listOf<EggEntry>()

        fun submitList(list: List<EggEntry>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemCollectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount() = items.size

        class ViewHolder(private val binding: ItemCollectionBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(item: EggEntry) {
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                binding.tvDate.text = sdf.format(Date(item.date))
                binding.tvEggs.text = "${item.eggsCount} œufs"
                binding.tvBroken.text = "${item.brokenEggsCount} cassés"
            }
        }
    }
}
