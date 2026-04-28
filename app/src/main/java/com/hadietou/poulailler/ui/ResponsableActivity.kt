package com.hadietou.poulailler.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hadietou.poulailler.R
import com.hadietou.poulailler.BuildConfig
import com.hadietou.poulailler.databinding.ActivityResponsableBinding
import com.hadietou.poulailler.databinding.ItemAgentAdminBinding
import com.hadietou.poulailler.databinding.ItemLoginHistoryBinding
import com.hadietou.poulailler.repository.FirebaseRepository
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
        vm.checkUserStatus()
    }

    private fun setupClickListeners() {
        binding.btnCreateAgent.setOnClickListener {
            val name = binding.etAgentName.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "Veuillez saisir le nom de l'agent", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            vm.createAgentSimplified(name) { login, pass ->
                firebaseRepo.shareAgentCredentials(this, login, pass)
                binding.etAgentName.setText("")
                Toast.makeText(this, "Identifiants générés et prêts à être partagés", Toast.LENGTH_LONG).show()
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
        val tvAppVersion = headerView.findViewById<TextView>(R.id.tvAppVersion)

        tvAppVersion?.text = "v${BuildConfig.VERSION_NAME}"

        lifecycleScope.launch {
            val uid = userId ?: FirebaseAuth.getInstance().currentUser?.uid
            if (uid != null) {
                val profile = firebaseRepo.getUserProfile(uid)
                withContext(Dispatchers.Main) {
                    val nameToShow = if (profile?.username.isNullOrEmpty() || profile?.username == "Utilisateur") {
                        profile?.role ?: "UTILISATEUR"
                    } else {
                        profile?.username ?: "UTILISATEUR"
                    }
                    tvUsername.text = nameToShow.uppercase()
                    tvUserRole.text = profile?.role ?: userRole
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

        vm.isUserPending.observe(this) { isPending ->
            if (isPending) {
                binding.btnCreateAgent.isEnabled = false
                binding.btnCreateAgent.alpha = 0.5f
                binding.tilAgentName.isEnabled = false
                Toast.makeText(this, "Création d'agents bloquée : Compte en attente de validation", Toast.LENGTH_LONG).show()
            } else {
                binding.btnCreateAgent.isEnabled = true
                binding.btnCreateAgent.alpha = 1.0f
                binding.tilAgentName.isEnabled = true
            }
        }

        vm.createAgentStatus.observe(this) { status ->
            val (success, msg) = status
            if (!success) {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateNavHeader()
        vm.checkUserStatus()
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
        var name = item["username"] as? String ?: ""
        val role = item["role"] as? String ?: "AGENT"
        val active = item["active"] as? Boolean ?: true

        if (name.isEmpty() || name == "Inconnu") {
            name = role
        }

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
        var name = item["username"] as? String ?: ""
        val timestamp = item["timestamp"] as? Long ?: 0L
        
        if (name.isEmpty() || name == "Inconnu") {
            name = "Utilisateur"
        }

        holder.binding.tvHistoryUsername.text = name
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        holder.binding.tvHistoryTime.text = if (timestamp > 0) sdf.format(Date(timestamp)) else "--"
    }

    override fun getItemCount() = items.size
}
