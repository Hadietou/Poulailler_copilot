package com.hadietou.poulailler.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import com.hadietou.poulailler.R
import com.hadietou.poulailler.BuildConfig
import com.hadietou.poulailler.data.Batch
import com.hadietou.poulailler.data.FarmInfo
import com.hadietou.poulailler.databinding.ActivityFarmInfoBinding
import com.hadietou.poulailler.repository.FirebaseRepository
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import com.hadietou.poulailler.util.NavMenuStyler
import com.hadietou.poulailler.util.NetworkStatusMonitor

class FarmInfoActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityFarmInfoBinding
    private var currentFarmInfo: FarmInfo? = null
    private var currentBatch: Batch? = null
    private val firebaseRepo = FirebaseRepository()

    private val currencies = arrayOf("MRU", "CFA")
    private var userRole: String = "AGENT"
    private var userId: String? = null
    private var isBlocked = false
    
    private var isEggMenuExpanded = false
    private var isHealthMenuExpanded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFarmInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        NetworkStatusMonitor.observe(this, binding.root)

        userRole = intent.getStringExtra("role") ?: "AGENT"
        userId = intent.getStringExtra("userIdString") ?: FirebaseAuth.getInstance().currentUser?.uid

        setupNavigation()
        setupCurrencyDropdown()
        loadExistingData()
        observeCurrentBatch()
        checkAccessStatus()

        binding.btnSaveFarmInfo.setOnClickListener {
            if (isBlocked) { showBlockingDialog(); return@setOnClickListener }
            saveData()
        }

        binding.btnEditInfo.setOnClickListener {
            if (isBlocked) { showBlockingDialog(); return@setOnClickListener }
            showEditMode(true)
        }

        binding.btnCancelEdit.setOnClickListener {
            showEditMode(false)
        }

        binding.btnSaveRation.setOnClickListener {
            if (isBlocked) { showBlockingDialog(); return@setOnClickListener }
            saveManualRation()
        }

        binding.cardFeedRation.visibility = if (userRole == "RESPONSABLE") View.VISIBLE else View.GONE
    }

    private fun observeCurrentBatch() {
        lifecycleScope.launch {
            firebaseRepo.getBatchesFlow().collectLatest { batches ->
                val batch = batches.find { it.status == "ACTIVE" }
                if (batch != null) {
                    currentBatch = batch
                    withContext(Dispatchers.Main) {
                        val rationG = (batch.feedRation * 1000).toInt()
                        if (binding.etFeedRation.text.isNullOrEmpty()) {
                            binding.etFeedRation.setText(rationG.toString())
                        }
                    }
                }
            }
        }
    }

    private fun saveManualRation() {
        val batch = currentBatch ?: run {
            Toast.makeText(this, "Aucun lot sélectionné", Toast.LENGTH_SHORT).show()
            return
        }

        val rationStr = binding.etFeedRation.text?.toString() ?: ""
        if (rationStr.isEmpty()) {
            Toast.makeText(this, "Veuillez saisir une valeur", Toast.LENGTH_SHORT).show()
            return
        }

        val rationG = rationStr.toDoubleOrNull() ?: 0.0
        val rationKg = rationG / 1000.0

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val updatedBatch = batch.copy(feedRation = rationKg)
                firebaseRepo.updateBatch(updatedBatch)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FarmInfoActivity, "Ration mise à jour : ${rationG.toInt()}g/sujet", Toast.LENGTH_SHORT).show()
                    binding.etFeedRation.clearFocus()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FarmInfoActivity, "Erreur : ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun checkAccessStatus() {
        lifecycleScope.launch {
            val blocked = firebaseRepo.isFarmAccessBlocked()
            isBlocked = blocked
            if (blocked) {
                withContext(Dispatchers.Main) {
                    binding.btnEditInfo.visibility = View.GONE
                    binding.btnSaveRation.isEnabled = false
                    showBlockingDialog()
                }
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

    private fun setupNavigation() {
        setSupportActionBar(binding.toolbar)
        val toggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

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
        menu.setGroupVisible(R.id.group_egg_submenu, isEggMenuExpanded)
        menu.setGroupVisible(R.id.group_health_submenu, isHealthMenuExpanded)
        refreshDrawerMenuStyle()
    }

    private fun refreshDrawerMenuStyle() {
        NavMenuStyler.style(
            binding.navigationView,
            this,
            defaultIconTintRes = R.color.text_secondary,
            parents = listOf(
                R.id.nav_egg_management to isEggMenuExpanded,
                R.id.nav_health_management to isHealthMenuExpanded
            ),
            children = listOf(R.id.nav_collect, R.id.nav_sales, R.id.nav_vaccines, R.id.nav_mortality)
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

    private fun setupCurrencyDropdown() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, currencies)
        binding.actvCurrency.setAdapter(adapter)
        binding.actvCurrency.setText(currencies[0], false)
    }

    private fun loadExistingData() {
        lifecycleScope.launch {
            val info = firebaseRepo.getFarmInfo()
            withContext(Dispatchers.Main) {
                if (info != null && info.farmName.isNotEmpty()) {
                    currentFarmInfo = info
                    displayInfo(info)
                    if (!isBlocked) showEditMode(false)
                } else {
                    showEditMode(true)
                }
            }
        }
    }

    private fun displayInfo(info: FarmInfo) {
        binding.tvDisplayFarmName.text = info.farmName
        binding.tvDisplayCurrency.text = info.currency

        binding.etFarmName.setText(info.farmName)
        binding.actvCurrency.setText(info.currency, false)

        binding.etEggTraysCritical.setText(info.eggTraysCriticalThreshold.toString())
        binding.etFeedStockCriticalDays.setText(info.feedStockCriticalDays.toString())
        binding.etFeedStockWarningDays.setText(info.feedStockWarningDays.toString())
        binding.etHeatAlertTemp.setText(info.heatAlertTempCelsius.toString())
        binding.etLightingHours.setText(info.lightingHoursAfterSunrise.toString())
        binding.etVaccineInterval.setText(info.vaccineIntervalMonths.toString())
        binding.etDewormingInternalInterval.setText(info.dewormingInternalIntervalMonths.toString())
        binding.etDewormingExternalInterval.setText(info.dewormingExternalIntervalMonths.toString())

        binding.tvDisplayThresholds.text = getString(
            R.string.thresholds_summary,
            info.eggTraysCriticalThreshold,
            info.feedStockCriticalDays,
            info.feedStockWarningDays,
            info.heatAlertTempCelsius,
            info.lightingHoursAfterSunrise,
            info.vaccineIntervalMonths,
            info.dewormingInternalIntervalMonths,
            info.dewormingExternalIntervalMonths
        )

        binding.tvDisplayHensCount.visibility = View.GONE
        binding.tvDisplayBreed.visibility = View.GONE
        binding.tvDisplayArrival.visibility = View.GONE
        binding.tvDisplayBirth.visibility = View.GONE
        
        binding.tilHensCount.visibility = View.GONE
        binding.tilHenBreed.visibility = View.GONE
        binding.tilArrivalDate.visibility = View.GONE
        binding.tilBirthDate.visibility = View.GONE
    }

    private fun showEditMode(isEditing: Boolean) {
        if (isEditing) {
            binding.layoutDisplayInfo.visibility = View.GONE
            binding.layoutEditInfo.visibility = View.VISIBLE
            binding.btnCancelEdit.visibility = if (currentFarmInfo != null) View.VISIBLE else View.GONE
        } else {
            binding.layoutDisplayInfo.visibility = View.VISIBLE
            binding.layoutEditInfo.visibility = View.GONE
        }
    }

    private fun saveData() {
        val name = binding.etFarmName.text.toString()
        val currency = binding.actvCurrency.text.toString()

        if (name.isEmpty()) {
            Toast.makeText(this, "Veuillez saisir le nom de la ferme", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val info = FarmInfo(
                    id = 1,
                    farmName = name,
                    currency = currency,
                    eggTraysCriticalThreshold = binding.etEggTraysCritical.text.toString().toIntOrNull()
                        ?: FarmInfo.DEFAULT_EGG_TRAYS_CRITICAL,
                    feedStockCriticalDays = binding.etFeedStockCriticalDays.text.toString().toIntOrNull()
                        ?: FarmInfo.DEFAULT_FEED_STOCK_CRITICAL_DAYS,
                    feedStockWarningDays = binding.etFeedStockWarningDays.text.toString().toIntOrNull()
                        ?: FarmInfo.DEFAULT_FEED_STOCK_WARNING_DAYS,
                    heatAlertTempCelsius = binding.etHeatAlertTemp.text.toString().toIntOrNull()
                        ?: FarmInfo.DEFAULT_HEAT_ALERT_TEMP,
                    lightingHoursAfterSunrise = binding.etLightingHours.text.toString().toIntOrNull()
                        ?: FarmInfo.DEFAULT_LIGHTING_HOURS,
                    vaccineIntervalMonths = binding.etVaccineInterval.text.toString().toIntOrNull()
                        ?: FarmInfo.DEFAULT_VACCINE_INTERVAL_MONTHS,
                    dewormingInternalIntervalMonths = binding.etDewormingInternalInterval.text.toString().toIntOrNull()
                        ?: FarmInfo.DEFAULT_DEWORMING_INTERNAL_MONTHS,
                    dewormingExternalIntervalMonths = binding.etDewormingExternalInterval.text.toString().toIntOrNull()
                        ?: FarmInfo.DEFAULT_DEWORMING_EXTERNAL_MONTHS
                )
                firebaseRepo.saveFarmInfo(info)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FarmInfoActivity, "Informations enregistrées", Toast.LENGTH_SHORT).show()
                    loadExistingData()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FarmInfoActivity, e.message ?: "Erreur", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        if (isBlocked && item.itemId != R.id.nav_logout && item.itemId != R.id.nav_dashboard && item.itemId != R.id.nav_delete_account) {
            showBlockingDialog()
            return false
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
            
            R.id.nav_egg_management -> {
                isEggMenuExpanded = !isEggMenuExpanded
                if (isEggMenuExpanded) isHealthMenuExpanded = false
                rebuildDrawerMenu()
                return true
            }

            R.id.nav_health_management -> {
                isHealthMenuExpanded = !isHealthMenuExpanded
                if (isHealthMenuExpanded) isEggMenuExpanded = false
                rebuildDrawerMenu()
                return true
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
            R.id.nav_settings -> {}
            R.id.nav_sales -> {
                val intent = Intent(this, SalesActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                startActivity(intent)
            }
            R.id.nav_mortality -> {
                val intent = Intent(this, MortalityActivity::class.java)
                intent.putExtra("userIdString", userId)
                intent.putExtra("role", userRole)
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
        checkAccessStatus()
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}
