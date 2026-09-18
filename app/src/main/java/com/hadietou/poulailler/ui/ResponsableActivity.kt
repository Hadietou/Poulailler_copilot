package com.hadietou.poulailler.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
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
import com.hadietou.poulailler.util.DrawerSubmenuController
import com.hadietou.poulailler.util.NavMenuStyler
import com.hadietou.poulailler.util.NetworkStatusMonitor

class ResponsableActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityResponsableBinding
    private val vm: ResponsableViewModel by viewModels()
    private val firebaseRepo = FirebaseRepository()

    private var userRole: String = "RESPONSABLE"
    private var userId: String? = null
    private var isBlocked = false
    
    private val drawerSubmenus = DrawerSubmenuController()

    private val countryDialOptions = listOf(
        "🇲🇷 +222" to "+222",
        "🇸🇳 +221" to "+221",
        "🇲🇱 +223" to "+223",
        "🇬🇳 +224" to "+224",
        "🇨🇮 +225" to "+225",
        "🇧🇯 +229" to "+229",
        "🇹🇬 +228" to "+228",
        "🇧🇫 +226" to "+226",
        "🇳🇪 +227" to "+227",
        "🇩🇿 +213" to "+213",
        "🇦🇴 +244" to "+244",
        "🇧🇼 +267" to "+267",
        "🇧🇮 +257" to "+257",
        "🇨🇻 +238" to "+238",
        "🇨🇲 +237" to "+237",
        "🇨🇫 +236" to "+236",
        "🇹🇩 +235" to "+235",
        "🇰🇲 +269" to "+269",
        "🇨🇬 +242" to "+242",
        "🇨🇩 +243" to "+243",
        "🇩🇯 +253" to "+253",
        "🇪🇬 +20" to "+20",
        "🇬🇶 +240" to "+240",
        "🇪🇷 +291" to "+291",
        "🇸🇿 +268" to "+268",
        "🇪🇹 +251" to "+251",
        "🇬🇦 +241" to "+241",
        "🇬🇲 +220" to "+220",
        "🇬🇭 +233" to "+233",
        "🇬🇼 +245" to "+245",
        "🇰🇪 +254" to "+254",
        "🇱🇸 +266" to "+266",
        "🇱🇷 +231" to "+231",
        "🇱🇾 +218" to "+218",
        "🇲🇬 +261" to "+261",
        "🇲🇼 +265" to "+265",
        "🇲🇺 +230" to "+230",
        "🇲🇦 +212" to "+212",
        "🇲🇿 +258" to "+258",
        "🇳🇦 +264" to "+264",
        "🇳🇬 +234" to "+234",
        "🇷🇼 +250" to "+250",
        "🇸🇹 +239" to "+239",
        "🇸🇨 +248" to "+248",
        "🇸🇱 +232" to "+232",
        "🇸🇴 +252" to "+252",
        "🇿🇦 +27" to "+27",
        "🇸🇸 +211" to "+211",
        "🇸🇩 +249" to "+249",
        "🇹🇿 +255" to "+255",
        "🇹🇳 +216" to "+216",
        "🇺🇬 +256" to "+256",
        "🇿🇲 +260" to "+260",
        "🇿🇼 +263" to "+263",
        "🇨🇭 +41" to "+41",
        "🇫🇷 +33" to "+33"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResponsableBinding.inflate(layoutInflater)
        setContentView(binding.root)
        NetworkStatusMonitor.observe(this, binding.root)

        userRole = intent.getStringExtra("role") ?: "RESPONSABLE"
        userId = intent.getStringExtra("userIdString") ?: FirebaseAuth.getInstance().currentUser?.uid

        setupNavigation()
        setupRecyclerViews()
        observeViewModel()
        setupClickListeners()
        setupDropdowns()

        vm.observeAgents()
        vm.observeLoginHistory()
        vm.checkUserStatus()
    }

    private fun setupDropdowns() {
        val codeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, countryDialOptions.map { it.first })
        binding.actvAgentCountryCode.setAdapter(codeAdapter)
        binding.actvAgentCountryCode.setText(countryDialOptions[0].first, false)
    }

    /**
     * Remplace l'ancien mécanisme d'identifiants pré-créés par une invitation adressée au
     * numéro de téléphone de l'agent (voir la note d'architecture dans FirebaseRepository) :
     * l'agent installe l'app, crée son compte avec ce numéro, et voit l'invitation dès qu'il
     * se connecte — que le message WhatsApp ait été lu ou non.
     */
    private fun setupClickListeners() {
        binding.btnCreateAgent.setOnClickListener {
            if (isBlocked) { showBlockingDialog(); return@setOnClickListener }

            val name = binding.etAgentName.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "Veuillez saisir le nom de l'agent", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val dialCode = countryDialOptions.find { it.first == binding.actvAgentCountryCode.text.toString() }?.second
                ?: countryDialOptions[0].second
            val digits = binding.etAgentPhone.text.toString().filter { it.isDigit() }
            if (digits.isEmpty()) {
                Toast.makeText(this, "Veuillez saisir le numéro de téléphone de l'agent", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val phoneNumber = "$dialCode$digits"

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val invitation = firebaseRepo.createInvitation(phoneNumber)
                    withContext(Dispatchers.Main) {
                        binding.etAgentName.setText("")
                        binding.etAgentPhone.setText("")
                        AlertDialog.Builder(this@ResponsableActivity)
                            .setTitle("Invitation ajoutée")
                            .setMessage("L'agent verra l'invitation dès qu'il se connecte à l'application. Voulez-vous aussi la lui envoyer par WhatsApp ?")
                            .setPositiveButton("Envoyer par WhatsApp") { _, _ ->
                                startActivity(firebaseRepo.buildWhatsAppInviteIntent(phoneNumber, invitation.farmName))
                            }
                            .setNegativeButton("Non, merci", null)
                            .show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ResponsableActivity, e.message ?: "Erreur", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun setupNavigation() {
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { onBackPressed() }

        binding.navigationView.setNavigationItemSelectedListener(this)

        rebuildDrawerMenu()
        updateNavHeader()
    }

    /**
     * NavigationView n'affiche pas toujours de manière fiable un item dont on vient
     * de changer la visibilité de groupe (menu.setGroupVisible) une fois déjà rendu à l'écran :
     * on force donc une reconstruction complète du menu avant de réappliquer les états courants.
     */
    private fun rebuildDrawerMenu() {
        val nav = binding.navigationView
        nav.menu.clear()
        nav.inflateMenu(R.menu.drawer_menu)
        val menu = nav.menu
        menu.findItem(R.id.nav_users)?.isVisible = userRole == "RESPONSABLE"
        menu.findItem(R.id.nav_expenses)?.isVisible = userRole == "RESPONSABLE"
        menu.findItem(R.id.nav_batches)?.isVisible = userRole == "RESPONSABLE"
        drawerSubmenus.applyGroupVisibility(menu)
        refreshDrawerMenuStyle()
    }

    private fun refreshDrawerMenuStyle() {
        NavMenuStyler.style(
            binding.navigationView,
            this,
            defaultIconTintRes = R.color.text_secondary,
            parents = drawerSubmenus.parentsForStyler(),
            children = DrawerSubmenuController.CHILD_ITEM_IDS
        )
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
        vm.isAccessBlocked.observe(this) { blocked ->
            isBlocked = blocked
            if (blocked) {
                binding.btnCreateAgent.isEnabled = false
                binding.btnCreateAgent.alpha = 0.5f
                binding.etAgentName.isEnabled = false
                showBlockingDialog()
            }
        }

        vm.agents.observe(this) { list ->
            binding.rvAgents.adapter = AgentAdapter(list, userId) { uid, active ->
                if (isBlocked) { showBlockingDialog() }
                else { vm.setAgentActive(uid, active) }
            }
            binding.tvEmptyAgents.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            binding.rvAgents.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        }

        vm.loginHistory.observe(this) { list ->
            val filteredList = list.take(10)
            binding.rvLoginHistory.adapter = LoginHistoryAdapter(filteredList)
        }

        vm.isUserPending.observe(this) { isPending ->
            if (isPending && !isBlocked) {
                Toast.makeText(this, "Note : Votre ferme est en attente de validation.", Toast.LENGTH_SHORT).show()
            }
        }

        vm.createAgentStatus.observe(this) { status ->
            val (success, msg) = status
            if (!success) {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showBlockingDialog() {
        AlertDialog.Builder(this)
            .setTitle("Mode Lecture Seule")
            .setMessage("Veuillez envoyer un email à hadietou@gmail.com pour lui demander de valider la ferme afin de continuer à utiliser l'application. En attendant, vous pouvez uniquement consulter vos données.")
            .setCancelable(true)
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        updateNavHeader()
        vm.checkUserStatus()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        if (isBlocked && item.itemId != R.id.nav_logout && item.itemId != R.id.nav_dashboard && item.itemId != R.id.nav_delete_account) {
            showBlockingDialog()
            return false
        }
        if (drawerSubmenus.handleParentClick(item.itemId)) {
            rebuildDrawerMenu()
            return true
        }

        when (item.itemId) {
            R.id.nav_dashboard -> {
                val intent = Intent(this, DashboardActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                startActivity(intent)
                finish()
            }
            R.id.nav_users -> {}

            R.id.nav_collect -> {
                val intent = Intent(this, AgentActivity::class.java)
                intent.putExtra("userIdString", userId)
                intent.putExtra("role", userRole)
                startActivity(intent)
            }
            R.id.nav_vaccines -> {
                val intent = Intent(this, HealthDashboardActivity::class.java)
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
            R.id.nav_settings -> {
                val intent = Intent(this, FarmInfoActivity::class.java)
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
            R.id.nav_mortality -> {
                val intent = Intent(this, MortalityActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                startActivity(intent)
            }
            R.id.nav_treatments -> {
                val intent = Intent(this, TreatmentActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                startActivity(intent)
            }
            R.id.nav_diseases -> {
                val intent = Intent(this, DiseaseActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                startActivity(intent)
            }
            R.id.nav_delete_account -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.delete_account_url)))
                startActivity(intent)
            }
            R.id.nav_logout -> {
                NavMenuStyler.confirmLogout(this) {
                    FirebaseAuth.getInstance().signOut()
                    val intent = Intent(this, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
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
