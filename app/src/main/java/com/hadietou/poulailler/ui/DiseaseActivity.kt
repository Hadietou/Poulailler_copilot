package com.hadietou.poulailler.ui

import android.app.AlertDialog
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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.hadietou.poulailler.BuildConfig
import com.hadietou.poulailler.R
import com.hadietou.poulailler.data.DiseaseCase
import com.hadietou.poulailler.databinding.ActivityDiseaseBinding
import com.hadietou.poulailler.databinding.DialogAddDiseaseBinding
import com.hadietou.poulailler.databinding.ItemDiseaseBinding
import com.hadietou.poulailler.repository.FirebaseRepository
import com.hadietou.poulailler.util.DrawerSubmenuController
import com.hadietou.poulailler.util.NavMenuStyler
import com.hadietou.poulailler.util.NetworkStatusMonitor
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Symptômes & maladies — dernier volet de la Phase 2. Distingue explicitement trois niveaux
 * de certitude (symptôme observé / maladie suspectée / diagnostic confirmé) : l'app ne pose
 * jamais elle-même de diagnostic, voir le bandeau d'avertissement en tête de page.
 */
class DiseaseActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityDiseaseBinding
    private val firebaseRepo = FirebaseRepository()
    private lateinit var adapter: DiseaseAdapter
    private val drawerSubmenus = DrawerSubmenuController(DrawerSubmenuController.Submenu.HEALTH)

    private var userRole: String = "AGENT"
    private var userId: String? = null
    private var selectedBatchId: String? = null
    private var allCases: List<DiseaseCase> = emptyList()
    private var isBlocked = false

    private val statusLabels = linkedMapOf(
        "SYMPTOME_OBSERVE" to "🔎 Symptôme observé",
        "MALADIE_SUSPECTEE" to "⚠️ Maladie suspectée",
        "DIAGNOSTIC_CONFIRME" to "🩺 Diagnostic confirmé"
    )
    private val severityLabels = linkedMapOf(
        "FAIBLE" to "🟢 Faible",
        "MOYENNE" to "🟠 Moyenne",
        "ELEVEE" to "🔴 Élevée"
    )

    private fun statusFromLabel(label: String) = statusLabels.entries.find { it.value == label }?.key ?: "SYMPTOME_OBSERVE"
    private fun severityFromLabel(label: String) = severityLabels.entries.find { it.value == label }?.key ?: "MOYENNE"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDiseaseBinding.inflate(layoutInflater)
        setContentView(binding.root)
        NetworkStatusMonitor.observe(this, binding.root)

        userRole = intent.getStringExtra("role") ?: "AGENT"
        userId = intent.getStringExtra("userIdString") ?: FirebaseAuth.getInstance().currentUser?.uid
        selectedBatchId = intent.getStringExtra("selectedBatchId")

        setupNavigation()
        setupRecyclerView()
        observeCases()
        checkAccessStatus()

        binding.fabAddDisease.setOnClickListener {
            if (isBlocked) { showBlockingDialog(); return@setOnClickListener }
            showAddDiseaseDialog()
        }
    }

    private fun checkAccessStatus() {
        lifecycleScope.launch {
            val blocked = firebaseRepo.isFarmAccessBlocked()
            isBlocked = blocked
            if (blocked) {
                withContext(Dispatchers.Main) {
                    binding.fabAddDisease.visibility = View.GONE
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

    private fun setupRecyclerView() {
        adapter = DiseaseAdapter(statusLabels, severityLabels) { case ->
            if (isBlocked) { showBlockingDialog(); return@DiseaseAdapter }
            showEditDiseaseDialog(case)
        }
        binding.rvDiseases.layoutManager = LinearLayoutManager(this)
        binding.rvDiseases.adapter = adapter
    }

    private fun observeCases() {
        lifecycleScope.launch {
            firebaseRepo.getDiseaseCasesFlow().collectLatest { list ->
                allCases = if (selectedBatchId != null) list.filter { it.batchId == selectedBatchId } else list
                withContext(Dispatchers.Main) {
                    binding.tvNoDiseases.visibility = if (allCases.isEmpty()) View.VISIBLE else View.GONE
                    binding.rvDiseases.visibility = if (allCases.isEmpty()) View.GONE else View.VISIBLE
                    adapter.submitList(allCases)
                }
            }
        }
    }

    private fun buildSymptomChips(chipGroup: com.google.android.material.chip.ChipGroup, preselected: List<String> = emptyList()): List<Chip> {
        chipGroup.removeAllViews()
        return DiseaseCase.COMMON_SYMPTOMS.map { symptom ->
            Chip(this).apply {
                text = symptom
                isCheckable = true
                isChecked = preselected.contains(symptom)
                chipGroup.addView(this)
            }
        }
    }

    private fun setupDropdowns(dialogBinding: DialogAddDiseaseBinding) {
        dialogBinding.actvDiseaseStatus.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, statusLabels.values.toList()))
        dialogBinding.actvDiseaseSeverity.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, severityLabels.values.toList()))
    }

    private fun showAddDiseaseDialog() {
        val dialogBinding = DialogAddDiseaseBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this).setView(dialogBinding.root).create()
        setupDropdowns(dialogBinding)
        val chips = buildSymptomChips(dialogBinding.chipGroupSymptoms)
        dialogBinding.actvDiseaseStatus.setText(statusLabels.getValue("SYMPTOME_OBSERVE"), false)
        dialogBinding.actvDiseaseSeverity.setText(severityLabels.getValue("MOYENNE"), false)

        dialogBinding.btnSaveDisease.setOnClickListener {
            val selectedSymptoms = chips.filter { it.isChecked }.map { it.text.toString() }
            val disease = DiseaseCase(
                suspectedDisease = dialogBinding.etSuspectedDisease.text?.toString()?.trim()?.ifEmpty { null },
                symptoms = selectedSymptoms,
                status = statusFromLabel(dialogBinding.actvDiseaseStatus.text?.toString() ?: ""),
                severity = severityFromLabel(dialogBinding.actvDiseaseSeverity.text?.toString() ?: ""),
                dateReported = System.currentTimeMillis(),
                affectedCount = dialogBinding.etAffectedCount.text?.toString()?.toIntOrNull(),
                deathCount = dialogBinding.etDeathCount.text?.toString()?.toIntOrNull(),
                zone = dialogBinding.etDiseaseZone.text?.toString()?.trim()?.ifEmpty { null },
                observerNotes = dialogBinding.etObserverNotes.text?.toString()?.trim()?.ifEmpty { null },
                vetDiagnosis = dialogBinding.etVetDiagnosis.text?.toString()?.trim()?.ifEmpty { null },
                actionsTaken = dialogBinding.etActionsTaken.text?.toString()?.trim()?.ifEmpty { null },
                batchId = selectedBatchId
            )
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    firebaseRepo.addDiseaseCase(disease)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@DiseaseActivity, "Cas enregistré", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@DiseaseActivity, e.message ?: "Erreur", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun showEditDiseaseDialog(case: DiseaseCase) {
        val dialogBinding = DialogAddDiseaseBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this).setView(dialogBinding.root).create()
        setupDropdowns(dialogBinding)
        val chips = buildSymptomChips(dialogBinding.chipGroupSymptoms, case.symptoms)

        dialogBinding.etSuspectedDisease.setText(case.suspectedDisease ?: "")
        dialogBinding.actvDiseaseStatus.setText(statusLabels.getValue(case.status), false)
        dialogBinding.actvDiseaseSeverity.setText(severityLabels.getValue(case.severity), false)
        dialogBinding.etAffectedCount.setText(case.affectedCount?.toString() ?: "")
        dialogBinding.etDeathCount.setText(case.deathCount?.toString() ?: "")
        dialogBinding.etDiseaseZone.setText(case.zone ?: "")
        dialogBinding.etObserverNotes.setText(case.observerNotes ?: "")
        dialogBinding.etVetDiagnosis.setText(case.vetDiagnosis ?: "")
        dialogBinding.etActionsTaken.setText(case.actionsTaken ?: "")

        dialogBinding.btnSaveDisease.text = "MODIFIER"
        dialogBinding.btnSaveDisease.setOnClickListener {
            val selectedSymptoms = chips.filter { it.isChecked }.map { it.text.toString() }
            val updated = case.copy(
                suspectedDisease = dialogBinding.etSuspectedDisease.text?.toString()?.trim()?.ifEmpty { null },
                symptoms = selectedSymptoms,
                status = statusFromLabel(dialogBinding.actvDiseaseStatus.text?.toString() ?: ""),
                severity = severityFromLabel(dialogBinding.actvDiseaseSeverity.text?.toString() ?: ""),
                affectedCount = dialogBinding.etAffectedCount.text?.toString()?.toIntOrNull(),
                deathCount = dialogBinding.etDeathCount.text?.toString()?.toIntOrNull(),
                zone = dialogBinding.etDiseaseZone.text?.toString()?.trim()?.ifEmpty { null },
                observerNotes = dialogBinding.etObserverNotes.text?.toString()?.trim()?.ifEmpty { null },
                vetDiagnosis = dialogBinding.etVetDiagnosis.text?.toString()?.trim()?.ifEmpty { null },
                actionsTaken = dialogBinding.etActionsTaken.text?.toString()?.trim()?.ifEmpty { null }
            )
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    firebaseRepo.updateDiseaseCase(updated)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@DiseaseActivity, "Cas modifié", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@DiseaseActivity, e.message ?: "Erreur", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        val deleteButton = TextView(this).apply {
            text = "SUPPRIMER CE CAS"
            setPadding(0, 48, 0, 0)
            setTextColor(getColor(R.color.error))
            gravity = android.view.Gravity.CENTER
            setOnClickListener {
                AlertDialog.Builder(this@DiseaseActivity)
                    .setTitle("Suppression")
                    .setMessage("Voulez-vous supprimer ce cas ?")
                    .setPositiveButton("Supprimer") { _, _ ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                case.firestoreId?.let { firebaseRepo.deleteDiseaseCase(it) }
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@DiseaseActivity, "Cas supprimé", Toast.LENGTH_SHORT).show()
                                    dialog.dismiss()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@DiseaseActivity, e.message ?: "Erreur", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    .setNegativeButton("Annuler", null)
                    .show()
            }
        }
        (dialogBinding.etActionsTaken.parent?.parent as? ViewGroup)?.addView(deleteButton)

        dialog.show()
    }

    class DiseaseAdapter(
        private val statusLabels: Map<String, String>,
        private val severityLabels: Map<String, String>,
        private val onItemClick: (DiseaseCase) -> Unit
    ) : RecyclerView.Adapter<DiseaseAdapter.ViewHolder>() {
        private var items = listOf<DiseaseCase>()

        fun submitList(list: List<DiseaseCase>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemDiseaseBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding, statusLabels, severityLabels)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.bind(item)
            holder.itemView.setOnClickListener { onItemClick(item) }
        }

        override fun getItemCount() = items.size

        class ViewHolder(
            private val binding: ItemDiseaseBinding,
            private val statusLabels: Map<String, String>,
            private val severityLabels: Map<String, String>
        ) : RecyclerView.ViewHolder(binding.root) {
            fun bind(item: DiseaseCase) {
                val context = binding.root.context
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

                binding.tvDiseaseName.text = item.suspectedDisease?.let { "Suspicion : $it" }
                    ?: item.symptoms.firstOrNull()
                    ?: "Cas sanitaire"

                binding.tvDiseaseStatus.text = statusLabels[item.status] ?: item.status
                val statusColor = when (item.status) {
                    "DIAGNOSTIC_CONFIRME" -> R.color.error
                    "MALADIE_SUSPECTEE" -> R.color.earthy_orange
                    else -> R.color.accent_blue
                }
                tintBadge(binding.tvDiseaseStatus, context.getColor(statusColor))

                binding.tvDiseaseSeverity.text = severityLabels[item.severity]?.substringAfter(" ") ?: item.severity
                val severityColor = when (item.severity) {
                    "ELEVEE" -> R.color.error
                    "FAIBLE" -> R.color.emerald_soft
                    else -> R.color.earthy_orange
                }
                tintBadge(binding.tvDiseaseSeverity, context.getColor(severityColor))

                binding.tvDiseaseSymptoms.text = if (item.symptoms.isNotEmpty()) item.symptoms.joinToString(", ") else "Aucun symptôme précisé"

                val counts = listOfNotNull(
                    item.affectedCount?.let { "$it concernée(s)" },
                    item.deathCount?.let { "$it mortes" },
                    item.zone?.let { "Zone : $it" }
                ).joinToString(" · ")
                binding.tvDiseaseCounts.text = counts
                binding.tvDiseaseCounts.visibility = if (counts.isEmpty()) View.GONE else View.VISIBLE

                binding.tvDiseaseDate.text = "Signalé le ${sdf.format(java.util.Date(item.dateReported))}"
            }

            private fun tintBadge(view: TextView, color: Int) {
                view.setTextColor(color)
                (view.background.mutate() as? android.graphics.drawable.GradientDrawable)?.setColor(
                    android.graphics.Color.argb(30, android.graphics.Color.red(color), android.graphics.Color.green(color), android.graphics.Color.blue(color))
                )
            }
        }
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
            R.id.nav_vaccines -> {
                val intent = Intent(this, HealthDashboardActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                intent.putExtra("selectedBatchId", selectedBatchId)
                startActivity(intent)
            }
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
            R.id.nav_diseases -> {}
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
        lifecycleScope.launch {
            isBlocked = firebaseRepo.isFarmAccessBlocked()
        }
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}
