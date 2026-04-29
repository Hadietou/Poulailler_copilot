package com.hadietou.poulailler.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hadietou.poulailler.data.AppDatabase
import com.hadietou.poulailler.data.LoginEntry
import com.hadietou.poulailler.databinding.ActivityLoginHistoryBinding
import com.hadietou.poulailler.databinding.ItemLoginBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class LoginHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginHistoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val adapter = LoginAdapter()
        binding.rvLoginHistory.layoutManager = LinearLayoutManager(this)
        binding.rvLoginHistory.adapter = adapter

        loadHistory(adapter)

        binding.btnClose.setOnClickListener { finish() }
    }

    private fun loadHistory(adapter: LoginAdapter) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(this@LoginHistoryActivity)
            val logins = db.loginDao().getAll()
            
            val dataList = logins.map { entry ->
                val user = db.userDao().getByUid(entry.userId)
                val username = user?.username ?: entry.username.ifEmpty { "Inconnu" }
                LoginDisplayItem(username, entry.timestamp)
            }

            withContext(Dispatchers.Main) {
                adapter.submitList(dataList)
            }
        }
    }

    data class LoginDisplayItem(val username: String, val timestamp: Long)

    class LoginAdapter : RecyclerView.Adapter<LoginAdapter.ViewHolder>() {
        private var items = listOf<LoginDisplayItem>()

        fun submitList(list: List<LoginDisplayItem>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemLoginBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount() = items.size

        class ViewHolder(private val binding: ItemLoginBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(item: LoginDisplayItem) {
                val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                binding.tvUsername.text = item.username
                binding.tvLoginTime.text = sdf.format(Date(item.timestamp))
            }
        }
    }
}
