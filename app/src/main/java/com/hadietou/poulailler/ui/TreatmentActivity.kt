package com.hadietou.poulailler.ui

import android.app.AlertDialog
import android.app.DatePickerDialog
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
import com.hadietou.poulailler.BuildConfig
import com.hadietou.poulailler.R
import com.hadietou.poulailler.data.Treatment
import com.hadietou.poulailler.databinding.ActivityTreatmentBinding
import com.hadietou.poulailler.databinding.DialogAddTreatmentBinding
import com.hadietou.poulailler.databinding.ItemTreatmentBinding
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
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Suivi des traitements/médicaments — Phase 2 de la refonte du Suivi Sanitaire.
 * Le point central de cette page est le délai d'attente (œufs/abattage), saisi
 * manuellement par l'éleveur (aucune base de référence par molécule dans l'app).
 */
class TreatmentActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityTreatmentBinding
    private val calendar = Calendar.getInstance()
    private val firebaseRepo = FirebaseRepository()
    private lateinit var adapter: TreatmentAdapter
    private val drawerSubmenus = DrawerSubmenuController(DrawerSubmenuController.Submenu.HEALTH)

    private var userRole: String = "AGENT"
    private var userId: String? = null
    private var selectedBatchId: String? = null
    private var allTreatments: List<Treatment> = emptyList()
    private var isBlocked = false

    private val statusLabels = linkedMapOf(
        "PREVU" to "🗓️ Prévu",
        "EN_COURS" to "💊 En cours",
        "TERMINE" to "✅ Terminé"
    )

    private fun statusFromLabel(label: String): String =
        statusLabels.entries.find { it.value == label }?.key ?: "EN_COURS"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTreatmentBinding.inflate(layoutInflater)
        setContentView(binding.root)
        NetworkStatusMonitor.observe(this, binding.root)

        userRole = intent.getStringExtra("role") ?: "AGENT"
        userId = intent.getStringExtra("userIdString") ?: FirebaseAuth.getInstance().currentUser?.uid
        selectedBatchId = intent.getStringExtra("selectedBatchId")

        setupNavigation()
        setupRecyclerView()
        observeTreatments()
        checkAccessStatus()

        binding.fabAddTreatment.setOnClickListener {
            if (isBlocked) { showBlockingDialog(); return@setOnClickListener }
            showAddTreatmentDialog()
        }
    }

    private fun checkAccessStatus() {
        lifecycleScope.launch {
            val blocked = firebaseRepo.isFarmAccessBlocked()
            isBlocked = blocked
            if (blocked) {
                withContext(Dispatchers.Main) {
                    binding.fabAddTreatment.visibility = View.GONE
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
        adapter = TreatmentAdapter { treatment ->
            if (isBlocked) { showBlockingDialog(); return@TreatmentAdapter }
            showEditTreatmentDialog(treatment)
        }
        binding.rvTreatments.layoutManager = LinearLayoutManager(this)
        binding.rvTreatments.adapter = adapter
    }

    private fun observeTreatments() {
        lifecycleScope.launch {
            firebaseRepo.getTreatmentsFlow().collectLatest { list ->
                allTreatments = if (selectedBatchId != null) list.filter { it.batchId == selectedBatchId } else list
                withContext(Dispatchers.Main) {
                    refreshDisplay()
                    refreshWithdrawalBanner()
                }
            }
        }
    }

    private fun refreshDisplay() {
        binding.tvNoTreatments.visibility = if (allTreatments.isEmpty()) View.VISIBLE else View.GONE
        binding.rvTreatments.visibility = if (allTreatments.isEmpty()) View.GONE else View.VISIBLE
        adapter.submitList(allTreatments)
    }

    private fun refreshWithdrawalBanner() {
        val now = System.currentTimeMillis()
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val activeEgg = allTreatments.filter { it.eggWithdrawalUntil?.let { until -> until > now } == true }
            .maxByOrNull { it.eggWithdrawalUntil!! }
        val activeSlaughter = allTreatments.filter { it.slaughterWithdrawalUntil?.let { until -> until > now } == true }
            .maxByOrNull { it.slaughterWithdrawalUntil!! }

        if (activeEgg == null && activeSlaughter == null) {
            binding.cardWithdrawalWarning.visibility = View.GONE
            return
        }
        binding.cardWithdrawalWarning.visibility = View.VISIBLE
        val lines = mutableListOf<String>()
        activeEgg?.let { lines += "Œufs (${it.medicationName}) : ne pas commercialiser avant le ${sdf.format(Date(it.eggWithdrawalUntil!!))}" }
        activeSlaughter?.let { lines += "Abattage (${it.medicationName}) : à éviter avant le ${sdf.format(Date(it.slaughterWithdrawalUntil!!))}" }
        binding.tvWithdrawalWarningDetail.text = lines.joinToString("\n")
    }

    private fun setupStatusDropdown(dialogBinding: DialogAddTreatmentBinding) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, statusLabels.values.toList())
        dialogBinding.actvTreatmentStatus.setAdapter(adapter)
    }

    private fun showAddTreatmentDialog() {
        val dialogBinding = DialogAddTreatmentBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this).setView(dialogBinding.root).create()
        setupStatusDropdown(dialogBinding)
        dialogBinding.actvTreatmentStatus.setText(statusLabels.getValue("EN_COURS"), false)

        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        var startDateMs = System.currentTimeMillis()
        var endDateMs: Long? = null
        dialogBinding.btnSelectStartDate.text = "Début: ${sdf.format(Date(startDateMs))}"

        dialogBinding.btnSelectStartDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                val c = Calendar.getInstance(); c.set(y, m, d)
                startDateMs = c.timeInMillis
                dialogBinding.btnSelectStartDate.text = "Début: ${sdf.format(c.time)}"
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }
        dialogBinding.btnSelectEndDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                val c = Calendar.getInstance(); c.set(y, m, d)
                endDateMs = c.timeInMillis
                dialogBinding.btnSelectEndDate.text = "Fin: ${sdf.format(c.time)}"
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        dialogBinding.btnSaveTreatment.setOnClickListener {
            val name = dialogBinding.etMedicationName.text?.toString()?.trim() ?: ""
            if (name.isEmpty()) {
                Toast.makeText(this, "Veuillez saisir le nom du médicament", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val treatment = Treatment(
                medicationName = name,
                reason = dialogBinding.etReason.text?.toString()?.trim()?.ifEmpty { null },
                startDate = startDateMs,
                endDate = endDateMs,
                dose = dialogBinding.etTreatmentDose.text?.toString()?.trim()?.ifEmpty { null },
                route = dialogBinding.etTreatmentRoute.text?.toString()?.trim()?.ifEmpty { null },
                prescriber = dialogBinding.etPrescriber.text?.toString()?.trim()?.ifEmpty { null },
                status = statusFromLabel(dialogBinding.actvTreatmentStatus.text?.toString() ?: ""),
                eggWithdrawalDays = dialogBinding.etEggWithdrawalDays.text?.toString()?.toIntOrNull(),
                slaughterWithdrawalDays = dialogBinding.etSlaughterWithdrawalDays.text?.toString()?.toIntOrNull(),
                notes = dialogBinding.etTreatmentNotes.text?.toString()?.trim()?.ifEmpty { null },
                batchId = selectedBatchId
            )
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    firebaseRepo.addTreatment(treatment)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@TreatmentActivity, "Traitement enregistré", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@TreatmentActivity, e.message ?: "Erreur", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun showEditTreatmentDialog(treatment: Treatment) {
        val dialogBinding = DialogAddTreatmentBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this).setView(dialogBinding.root).create()
        setupStatusDropdown(dialogBinding)

        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        dialogBinding.etMedicationName.setText(treatment.medicationName)
        dialogBinding.etReason.setText(treatment.reason ?: "")
        dialogBinding.actvTreatmentStatus.setText(statusLabels.getValue(treatment.status), false)
        dialogBinding.etTreatmentDose.setText(treatment.dose ?: "")
        dialogBinding.etTreatmentRoute.setText(treatment.route ?: "")
        dialogBinding.etPrescriber.setText(treatment.prescriber ?: "")
        dialogBinding.etEggWithdrawalDays.setText(treatment.eggWithdrawalDays?.toString() ?: "")
        dialogBinding.etSlaughterWithdrawalDays.setText(treatment.slaughterWithdrawalDays?.toString() ?: "")
        dialogBinding.etTreatmentNotes.setText(treatment.notes ?: "")

        var startDateMs = treatment.startDate
        var endDateMs = treatment.endDate
        dialogBinding.btnSelectStartDate.text = "Début: ${sdf.format(Date(startDateMs))}"
        dialogBinding.btnSelectEndDate.text = if (endDateMs != null) "Fin: ${sdf.format(Date(endDateMs!!))}" else "Fin (optionnel)"

        dialogBinding.btnSelectStartDate.setOnClickListener {
            val c = Calendar.getInstance().apply { timeInMillis = startDateMs }
            DatePickerDialog(this, { _, y, m, d ->
                val tempCal = Calendar.getInstance(); tempCal.set(y, m, d)
                startDateMs = tempCal.timeInMillis
                dialogBinding.btnSelectStartDate.text = "Début: ${sdf.format(tempCal.time)}"
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
        }
        dialogBinding.btnSelectEndDate.setOnClickListener {
            val c = Calendar.getInstance().apply { endDateMs?.let { timeInMillis = it } }
            DatePickerDialog(this, { _, y, m, d ->
                val tempCal = Calendar.getInstance(); tempCal.set(y, m, d)
                endDateMs = tempCal.timeInMillis
                dialogBinding.btnSelectEndDate.text = "Fin: ${sdf.format(tempCal.time)}"
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
        }

        dialogBinding.btnSaveTreatment.text = "MODIFIER"
        dialogBinding.btnSaveTreatment.setOnClickListener {
            val name = dialogBinding.etMedicationName.text?.toString()?.trim() ?: ""
            if (name.isEmpty()) {
                Toast.makeText(this, "Nom obligatoire", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val updated = treatment.copy(
                medicationName = name,
                reason = dialogBinding.etReason.text?.toString()?.trim()?.ifEmpty { null },
                startDate = startDateMs,
                endDate = endDateMs,
                dose = dialogBinding.etTreatmentDose.text?.toString()?.trim()?.ifEmpty { null },
                route = dialogBinding.etTreatmentRoute.text?.toString()?.trim()?.ifEmpty { null },
                prescriber = dialogBinding.etPrescriber.text?.toString()?.trim()?.ifEmpty { null },
                status = statusFromLabel(dialogBinding.actvTreatmentStatus.text?.toString() ?: ""),
                eggWithdrawalDays = dialogBinding.etEggWithdrawalDays.text?.toString()?.toIntOrNull(),
                slaughterWithdrawalDays = dialogBinding.etSlaughterWithdrawalDays.text?.toString()?.toIntOrNull(),
                notes = dialogBinding.etTreatmentNotes.text?.toString()?.trim()?.ifEmpty { null }
            )
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    firebaseRepo.updateTreatment(updated)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@TreatmentActivity, "Traitement modifié", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@TreatmentActivity, e.message ?: "Erreur", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        val deleteButton = TextView(this).apply {
            text = "SUPPRIMER CE TRAITEMENT"
            setPadding(0, 48, 0, 0)
            setTextColor(getColor(R.color.error))
            gravity = android.view.Gravity.CENTER
            setOnClickListener {
                AlertDialog.Builder(this@TreatmentActivity)
                    .setTitle("Suppression")
                    .setMessage("Voulez-vous supprimer ce traitement ?")
                    .setPositiveButton("Supprimer") { _, _ ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                treatment.firestoreId?.let { firebaseRepo.deleteTreatment(it) }
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@TreatmentActivity, "Traitement supprimé", Toast.LENGTH_SHORT).show()
                                    dialog.dismiss()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@TreatmentActivity, e.message ?: "Erreur", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    .setNegativeButton("Annuler", null)
                    .show()
            }
        }
        (dialogBinding.etTreatmentNotes.parent?.parent as? ViewGroup)?.addView(deleteButton)

        dialog.show()
    }

    class TreatmentAdapter(private val onItemClick: (Treatment) -> Unit) : RecyclerView.Adapter<TreatmentAdapter.ViewHolder>() {
        private var items = listOf<Treatment>()

        fun submitList(list: List<Treatment>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemTreatmentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.bind(item)
            holder.itemView.setOnClickListener { onItemClick(item) }
        }

        override fun getItemCount() = items.size

        class ViewHolder(private val binding: ItemTreatmentBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(item: Treatment) {
                val context = binding.root.context
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                binding.tvTreatmentName.text = item.medicationName

                if (!item.reason.isNullOrEmpty()) {
                    binding.tvTreatmentReason.text = "Motif : ${item.reason}"
                    binding.tvTreatmentReason.visibility = View.VISIBLE
                } else {
                    binding.tvTreatmentReason.visibility = View.GONE
                }

                binding.tvTreatmentDates.text = if (item.endDate != null) {
                    "Du ${sdf.format(Date(item.startDate))} au ${sdf.format(Date(item.endDate))}"
                } else {
                    "Depuis le ${sdf.format(Date(item.startDate))}"
                }

                val (label, colorRes) = when (item.status) {
                    "PREVU" -> "PRÉVU" to R.color.accent_blue
                    "TERMINE" -> "TERMINÉ" to R.color.emerald_soft
                    else -> "EN COURS" to R.color.earthy_orange
                }
                binding.tvTreatmentStatus.text = label
                val color = context.getColor(colorRes)
                binding.tvTreatmentStatus.setTextColor(color)
                (binding.tvTreatmentStatus.background.mutate() as? android.graphics.drawable.GradientDrawable)?.setColor(
                    android.graphics.Color.argb(30, android.graphics.Color.red(color), android.graphics.Color.green(color), android.graphics.Color.blue(color))
                )

                val now = System.currentTimeMillis()
                val warnings = mutableListOf<String>()
                item.eggWithdrawalUntil?.let { until -> if (until > now) warnings += "⛔ Œufs : délai jusqu'au ${sdf.format(Date(until))}" }
                item.slaughterWithdrawalUntil?.let { until -> if (until > now) warnings += "⛔ Abattage : délai jusqu'au ${sdf.format(Date(until))}" }
                if (warnings.isNotEmpty()) {
                    binding.tvTreatmentWithdrawal.text = warnings.joinToString("\n")
                    binding.tvTreatmentWithdrawal.visibility = View.VISIBLE
                } else {
                    binding.tvTreatmentWithdrawal.visibility = View.GONE
                }
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
            R.id.nav_treatments -> {}
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
