package com.hadietou.poulailler.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hadietou.poulailler.data.AppDatabase
import com.hadietou.poulailler.data.Expense
import com.hadietou.poulailler.databinding.ActivityExpenseHistoryBinding
import com.hadietou.poulailler.databinding.ItemExpenseBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class ExpenseHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExpenseHistoryBinding
    private val adapter = ExpenseAdapter()
    private var currentOffset = 0
    private val pageSize = 20

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExpenseHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        loadInitialData()

        binding.btnLoadMore.setOnClickListener {
            loadMoreData()
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        binding.rvExpenseHistory.layoutManager = LinearLayoutManager(this)
        binding.rvExpenseHistory.adapter = adapter
    }

    private fun loadInitialData() {
        currentOffset = 0
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(this@ExpenseHistoryActivity)
            val initialList = db.expenseDao().getPagedExpenses(pageSize, currentOffset)
            withContext(Dispatchers.Main) {
                adapter.submitList(initialList)
                currentOffset += initialList.size
                updateLoadMoreButton(initialList.size)
            }
        }
    }

    private fun loadMoreData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(this@ExpenseHistoryActivity)
            val newList = db.expenseDao().getPagedExpenses(pageSize, currentOffset)
            withContext(Dispatchers.Main) {
                adapter.appendList(newList)
                currentOffset += newList.size
                updateLoadMoreButton(newList.size)
            }
        }
    }

    private fun updateLoadMoreButton(lastFetchedSize: Int) {
        if (lastFetchedSize >= pageSize) {
            binding.btnLoadMore.visibility = View.VISIBLE
        } else {
            binding.btnLoadMore.visibility = View.GONE
        }
    }

    class ExpenseAdapter : RecyclerView.Adapter<ExpenseAdapter.ViewHolder>() {
        private var items = mutableListOf<Expense>()

        fun submitList(list: List<Expense>) {
            items = list.toMutableList()
            notifyDataSetChanged()
        }

        fun appendList(list: List<Expense>) {
            val startPos = items.size
            items.addAll(list)
            notifyItemRangeInserted(startPos, list.size)
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
