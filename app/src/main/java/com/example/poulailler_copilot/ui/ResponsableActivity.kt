package com.example.poulailler_copilot.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.poulailler_copilot.R
import com.example.poulailler_copilot.databinding.ActivityResponsableBinding
import com.example.poulailler_copilot.databinding.ItemAgentAdminBinding
import com.example.poulailler_copilot.databinding.ItemLoginHistoryBinding
import com.example.poulailler_copilot.repository.FirebaseRepository
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class ResponsableActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityResponsableBinding
    private val vm: ResponsableViewModel by viewModels()
    private val firebaseRepo = FirebaseRepository()

    private var userRole: String = "RESPONSABLE"
    private var userId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResponsableBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userRole = intent.getStringExtra("role") ?: "RESPONSABLE"
        userId = intent.getStringExtra("userIdString") ?: FirebaseAuth.getInstance().currentUser?.uid

        setupNavigation()
        setupRecyclerViews()
        observeViewModel()
        setupClickListeners()

        vm.observeAgents()
        vm.observeLoginHistory()
        vm.loadFarmCode()
    }

    private fun setupClickListeners() {
        binding.btnInviteAgent.setOnClickListener {
            val code = vm.farmCode.value
            if (code != null && code != "------") {
                firebaseRepo.shareInviteCode(this, code)
            }
        }
    }

    private fun setupNavigation() {
        setSupportActionBar(binding.toolbar)
        val toggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        binding.navigationView.setNavigationItemSelectedListener(this)
        
        val menu = binding.navigationView.menu
        menu.findItem(R.id.nav_users).isVisible = userRole == "RESPONSABLE"
        menu.findItem(R.id.nav_expenses).isVisible = userRole == "RESPONSABLE"
        menu.findItem(R.id.nav_vaccines).isVisible = userRole == "RESPONSABLE"
        menu.findItem(R.id.nav_farm_info).isVisible = userRole == "RESPONSABLE"

        updateNavHeader()
    }

    private fun updateNavHeader() {
        val headerView = binding.navigationView.getHeaderView(0)
        val tvUsername = headerView.findViewById<TextView>(R.id.tvUsername)
        val tvUserRole = headerView.findViewById<TextView>(R.id.tvUserRole)

        lifecycleScope.launch {
            val uid = userId ?: FirebaseAuth.getInstance().currentUser?.uid
            if (uid != null) {
                val profile = firebaseRepo.getUserProfile(uid)
                withContext(Dispatchers.Main) {
                    tvUsername.text = profile?.username?.uppercase() ?: "UTILISATEUR"
                    tvUserRole.text = userRole
                }
            }
        }
    }

    private fun setupRecyclerViews() {
        binding.rvAgents.layoutManager = LinearLayoutManager(this)
        binding.rvLoginHistory.layoutManager = LinearLayoutManager(this)
    }

    private fun observeViewModel() {
        vm.agents.observe(this) { list ->
            binding.rvAgents.adapter = AgentAdapter(list, userId) { uid, active ->
                vm.setAgentActive(uid, active)
            }
        }

        vm.loginHistory.observe(this) { list ->
            val filteredList = list.take(10)
            binding.rvLoginHistory.adapter = LoginHistoryAdapter(filteredList)
        }

        vm.farmCode.observe(this) { code ->
            binding.tvFarmCode.text = code
        }
    }

    override fun onResume() {
        super.onResume()
        updateNavHeader()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_dashboard -> {
                val intent = Intent(this, DashboardActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                startActivity(intent)
                finish()
            }
            R.id.nav_users -> {}
            R.id.nav_farm_info -> {
                val intent = Intent(this, FarmInfoActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                startActivity(intent)
            }
            R.id.nav_collect -> {
                val intent = Intent(this, AgentActivity::class.java)
                intent.putExtra("userIdString", userId)
                intent.putExtra("role", userRole)
                startActivity(intent)
            }
            R.id.nav_vaccines -> {
                val intent = Intent(this, VaccineActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                startActivity(intent)
            }
            R.id.nav_expenses -> {
                val intent = Intent(this, ExpensesActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                startActivity(intent)
            }
            R.id.nav_sales -> {
                val intent = Intent(this, SalesActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                startActivity(intent)
            }
            R.id.nav_logout -> {
                FirebaseAuth.getInstance().signOut()
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}

class AgentAdapter(
    private val items: List<Map<String, Any>>,
    private val currentUserId: String?,
    private val onStatusChanged: (String, Boolean) -> Unit
) : RecyclerView.Adapter<AgentAdapter.AgentViewHolder>() {

    class AgentViewHolder(val binding: ItemAgentAdminBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AgentViewHolder {
        val b = ItemAgentAdminBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AgentViewHolder(b)
    }

    override fun onBindViewHolder(holder: AgentViewHolder, position: Int) {
        val item = items[position]
        val uid = item["uid"] as? String ?: ""
        val name = item["username"] as? String ?: "Inconnu"
        val role = item["role"] as? String ?: "AGENT"
        val active = item["active"] as? Boolean ?: true

        holder.binding.tvAgentName.text = name
        holder.binding.tvAgentRole.text = "Rôle: $role"
        
        holder.binding.switchActive.isEnabled = uid != currentUserId
        
        holder.binding.switchActive.setOnCheckedChangeListener(null)
        holder.binding.switchActive.isChecked = active
        holder.binding.switchActive.setOnCheckedChangeListener { _, isChecked ->
            onStatusChanged(uid, isChecked)
        }
    }

    override fun getItemCount() = items.size
}

class LoginHistoryAdapter(private val items: List<Map<String, Any>>) :
    RecyclerView.Adapter<LoginHistoryAdapter.HistoryViewHolder>() {

    class HistoryViewHolder(val binding: ItemLoginHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val b = ItemLoginHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HistoryViewHolder(b)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val item = items[position]
        val name = item["username"] as? String ?: "Inconnu"
        val timestamp = item["timestamp"] as? Long ?: 0L

        holder.binding.tvHistoryUsername.text = name
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        holder.binding.tvHistoryTime.text = if (timestamp > 0) sdf.format(Date(timestamp)) else "--"
    }

    override fun getItemCount() = items.size
}
