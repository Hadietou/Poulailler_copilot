package com.example.poulailler_copilot.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.poulailler_copilot.data.AppDatabase
import com.example.poulailler_copilot.data.Expense
import com.example.poulailler_copilot.databinding.ActivityExpenseHistoryBinding
import com.example.poulailler_copilot.databinding.ItemExpenseBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ExpenseHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExpenseHistoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExpenseHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val adapter = ExpenseAdapter()
        binding.rvExpenseHistory.layoutManager = LinearLayoutManager(this)
        binding.rvExpenseHistory.adapter = adapter

        lifecycleScope.launch {
            val db = AppDatabase.getInstance(this@ExpenseHistoryActivity)
            db.expenseDao().getAllFlow().collectLatest { list ->
                adapter.submitList(list)
            }
        }

        binding.btnClose.setOnClickListener { finish() }
    }

    class ExpenseAdapter : RecyclerView.Adapter<ExpenseAdapter.ViewHolder>() {
        private var items = listOf<Expense>()

        fun submitList(list: List<Expense>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemExpenseBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount() = items.size

        class ViewHolder(private val binding: ItemExpenseBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(item: Expense) {
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                binding.tvDate.text = sdf.format(Date(item.date))
                binding.tvCategory.text = item.category
                binding.tvDescription.text = item.description
                binding.tvAmount.text = String.format("%.2f $", item.amount)
            }
        }
    }
}
