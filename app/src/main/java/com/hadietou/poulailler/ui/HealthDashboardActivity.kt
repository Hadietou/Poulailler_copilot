package com.hadietou.poulailler.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import com.hadietou.poulailler.BuildConfig
import com.hadietou.poulailler.R
import com.hadietou.poulailler.data.Batch
import com.hadietou.poulailler.databinding.ActivityHealthDashboardBinding
import com.hadietou.poulailler.repository.FirebaseRepository
import com.hadietou.poulailler.util.AlertLevel
import com.hadietou.poulailler.util.DrawerSubmenuController
import com.hadietou.poulailler.util.HealthAlertEngine
import com.hadietou.poulailler.util.NavMenuStyler
import com.hadietou.poulailler.util.NetworkStatusMonitor
import com.google.android.material.card.MaterialCardView
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Tableau de bord sanitaire — point d'entrée unique du module Suivi Sanitaire (menu "Suivi
 * Sanitaire" du tiroir). Réutilise [DashboardViewModel] pour l'effectif/l'âge du lot et la
 * mortalité (mêmes calculs que le dashboard principal), et [HealthAlertEngine] pour le fil
 * d'alertes. Distribue ensuite vers les sous-pages existantes (Vaccinations & Rappels,
 * Suivi de la mortalité) — voir la note d'architecture pour les pages à venir.
 */
class HealthDashboardActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityHealthDashboardBinding
    private val viewModel: DashboardViewModel by viewModels()
    private val firebaseRepo = FirebaseRepository()
    private val drawerSubmenus = DrawerSubmenuController(DrawerSubmenuController.Submenu.HEALTH)

    private var userRole: String = "AGENT"
    private var userId: String? = null
    private var selectedBatchId: String? = null
    private var allTreatments: List<com.hadietou.poulailler.data.Treatment> = emptyList()
    private var allDiseaseCases: List<com.hadietou.poulailler.data.DiseaseCase> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHealthDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        NetworkStatusMonitor.observe(this, binding.root)

        userRole = intent.getStringExtra("role") ?: "AGENT"
        userId = intent.getStringExtra("userIdString") ?: FirebaseAuth.getInstance().currentUser?.uid
        selectedBatchId = intent.getStringExtra("selectedBatchId")

        setupNavigation()
        setupClickListeners()
        observeViewModel()
        observeTreatments()
        observeDiseaseCases()
        viewModel.loadData()
    }

    private fun setupNavigation() {
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { onBackPressed() }
        binding.navigationView.setNavigationItemSelectedListener(this)
        rebuildDrawerMenu()
        updateNavHeader()
    }

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
                    tvUsername.text = profile?.username?.uppercase() ?: "UTILISATEUR"
                    tvUserRole.text = userRole
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.cardOpenVaccines.setOnClickListener {
            val intent = Intent(this, VaccineActivity::class.java)
            intent.putExtra("role", userRole)
            intent.putExtra("userIdString", userId)
            intent.putExtra("selectedBatchId", selectedBatchId)
            startActivity(intent)
        }
        binding.cardOpenMortality.setOnClickListener {
            val intent = Intent(this, MortalityActivity::class.java)
            intent.putExtra("role", userRole)
            intent.putExtra("userIdString", userId)
            intent.putExtra("selectedBatchId", selectedBatchId)
            startActivity(intent)
        }
        binding.cardOpenTreatments.setOnClickListener {
            val intent = Intent(this, TreatmentActivity::class.java)
            intent.putExtra("role", userRole)
            intent.putExtra("userIdString", userId)
            intent.putExtra("selectedBatchId", selectedBatchId)
            startActivity(intent)
        }
        binding.cardOpenDiseases.setOnClickListener {
            val intent = Intent(this, DiseaseActivity::class.java)
            intent.putExtra("role", userRole)
            intent.putExtra("userIdString", userId)
            intent.putExtra("selectedBatchId", selectedBatchId)
            startActivity(intent)
        }
    }

    private fun observeDiseaseCases() {
        lifecycleScope.launch {
            firebaseRepo.getDiseaseCasesFlow().collectLatest { list ->
                allDiseaseCases = if (selectedBatchId != null) list.filter { it.batchId == selectedBatchId } else list
                val now = System.currentTimeMillis()
                val recentCount = allDiseaseCases.count { now - it.dateReported <= 7 * 86_400_000L }
                binding.tvDiseasesSubtitle.text = if (recentCount > 0) "$recentCount cas (7 derniers jours)" else "Aucun cas récent"
                refreshAlerts()
            }
        }
    }

    private fun observeTreatments() {
        lifecycleScope.launch {
            firebaseRepo.getTreatmentsFlow().collectLatest { list ->
                allTreatments = if (selectedBatchId != null) list.filter { it.batchId == selectedBatchId } else list
                val now = System.currentTimeMillis()
                val activeWithdrawals = allTreatments.count {
                    (it.eggWithdrawalUntil?.let { u -> u > now } == true) || (it.slaughterWithdrawalUntil?.let { u -> u > now } == true)
                }
                val inProgress = allTreatments.count { it.status == "EN_COURS" }
                binding.tvTreatmentsSubtitle.text = when {
                    activeWithdrawals > 0 -> "⛔ $activeWithdrawals délai(s) d'attente en cours"
                    inProgress > 0 -> "$inProgress en cours"
                    else -> "Aucun traitement en cours"
                }
                refreshAlerts()
            }
        }
    }

    private fun observeViewModel() {
        viewModel.allBatches.observe(this) { batches ->
            if (viewModel.selectedBatch.value == null) {
                val batch = batches.firstOrNull { it.firestoreId == selectedBatchId }
                    ?: batches.firstOrNull { it.status == "ACTIVE" }
                    ?: batches.firstOrNull()
                batch?.let { viewModel.selectBatch(it) }
            }
        }

        viewModel.selectedBatch.observe(this) { batch -> latestBatch = batch; refreshBatchHeader() }

        viewModel.monthlyMortalityCount.observe(this) { count ->
            binding.tvMortalityMonth.text = count.toString()
        }

        viewModel.cumulativeMortalityRate.observe(this) { rate ->
            binding.tvMortalityRate.text = String.format(Locale.getDefault(), "%.1f%%", rate)
            val color = when {
                rate > 10.0 -> R.color.error
                rate > 5.0 -> R.color.earthy_orange
                else -> R.color.emerald_soft
            }
            binding.tvMortalityRate.setTextColor(getColor(color))
        }

        viewModel.activeHealthReminders.observe(this) { reminders ->
            binding.tvVaccinesSubtitle.text = if (reminders.isEmpty()) {
                "Aucun rappel en attente"
            } else {
                "${reminders.size} rappel(s) en attente"
            }
            refreshAlerts()
        }

        viewModel.allMortalities.observe(this) { list ->
            val today = list.orEmpty().filter { isToday(it.date) }.sumOf { it.count }
            binding.tvMortalityToday.text = today.toString()
            binding.tvMortalitySubtitle.text = if (today > 0) "$today perte(s) aujourd'hui" else "Enregistrer une perte"
            refreshBatchHeader()
            refreshAlerts()
        }
    }

    private fun isToday(dateMs: Long): Boolean {
        val cal1 = java.util.Calendar.getInstance().apply { timeInMillis = dateMs }
        val cal2 = java.util.Calendar.getInstance()
        return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
            cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR)
    }

    private var latestBatch: Batch? = null

    /**
     * Effectif courant = effectif initial du lot moins le cumul de mortalité de CE lot.
     * Recalculé ici plutôt que lu depuis [DashboardViewModel.effectiveHensCount] : cette
     * activité a sa propre instance du ViewModel (non partagée avec le Dashboard), et
     * cette dernière valeur dépend d'un recalcul interne qui peut s'exécuter avant que
     * toutes les données (mortalités incluses) soient arrivées de Firebase.
     */
    private fun refreshBatchHeader() {
        val batch = latestBatch
        if (batch == null) {
            binding.tvBatchName.text = "Aucun lot sélectionné"
            binding.tvBatchSummary.text = ""
            return
        }
        val totalMortality = viewModel.allMortalities.value.orEmpty()
            .filter { it.batchId == batch.firestoreId }
            .sumOf { it.count }
        val currentHens = (batch.hensCount - totalMortality).coerceAtLeast(0)
        binding.tvBatchName.text = "${batch.name} · ${batch.typeLot}"
        val age = viewModel.getFormattedAge(batch.chickBirthDate)
        binding.tvBatchSummary.text = "$currentHens sujets · $age"
    }

    private fun refreshAlerts() {
        val mortalities = viewModel.allMortalities.value.orEmpty()
        val reminders = viewModel.activeHealthReminders.value.orEmpty()
        val alerts = HealthAlertEngine.computeAll(mortalities, reminders, allTreatments, allDiseaseCases)

        binding.containerAlerts.removeAllViews()
        if (alerts.isEmpty()) {
            binding.containerAlerts.addView(buildAlertCard(
                title = "Tout va bien",
                message = "Aucune alerte sanitaire active pour le moment.",
                bgColorRes = R.color.emerald_container,
                textColorRes = R.color.emerald_soft
            ))
            return
        }
        alerts.forEach { alert ->
            val isCrit = alert.level == AlertLevel.CRITIQUE
            binding.containerAlerts.addView(buildAlertCard(
                title = (if (isCrit) "🔴 " else "🟠 ") + alert.title,
                message = alert.message,
                bgColorRes = if (isCrit) R.color.error_container else R.color.earthy_container,
                textColorRes = if (isCrit) R.color.error else R.color.earthy_orange
            ))
        }
    }

    private fun buildAlertCard(title: String, message: String, bgColorRes: Int, textColorRes: Int): MaterialCardView {
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (8 * resources.displayMetrics.density).toInt()
            }
            radius = 16 * resources.displayMetrics.density
            cardElevation = 0f
            setCardBackgroundColor(getColor(bgColorRes))
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (14 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        val tvTitle = TextView(this).apply {
            text = title
            setTextColor(getColor(textColorRes))
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val tvMessage = TextView(this).apply {
            text = message
            setTextColor(getColor(R.color.text_secondary))
            textSize = 12f
            setPadding(0, (4 * resources.displayMetrics.density).toInt(), 0, 0)
        }
        inner.addView(tvTitle)
        inner.addView(tvMessage)
        card.addView(inner)
        return card
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
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
            R.id.nav_batches -> {
                val intent = Intent(this, BatchActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                startActivity(intent)
            }
            R.id.nav_users -> {
                val intent = Intent(this, ResponsableActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                startActivity(intent)
            }
            R.id.nav_collect -> {
                val intent = Intent(this, AgentActivity::class.java)
                intent.putExtra("userIdString", userId)
                intent.putExtra("role", userRole)
                intent.putExtra("selectedBatchId", selectedBatchId)
                startActivity(intent)
            }
            R.id.nav_vaccines -> {}
            R.id.nav_expenses -> {
                val intent = Intent(this, ExpensesActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                intent.putExtra("selectedBatchId", selectedBatchId)
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
                intent.putExtra("selectedBatchId", selectedBatchId)
                startActivity(intent)
            }
            R.id.nav_mortality -> {
                val intent = Intent(this, MortalityActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                intent.putExtra("selectedBatchId", selectedBatchId)
                startActivity(intent)
            }
            R.id.nav_treatments -> {
                val intent = Intent(this, TreatmentActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                intent.putExtra("selectedBatchId", selectedBatchId)
                startActivity(intent)
            }
            R.id.nav_diseases -> {
                val intent = Intent(this, DiseaseActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                intent.putExtra("selectedBatchId", selectedBatchId)
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

    override fun onResume() {
        super.onResume()
        updateNavHeader()
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}
