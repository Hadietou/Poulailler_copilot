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
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hadietou.poulailler.R
import com.hadietou.poulailler.BuildConfig
import com.hadietou.poulailler.data.Mortality
import com.hadietou.poulailler.databinding.ActivityMortalityBinding
import com.hadietou.poulailler.databinding.DialogAddMortalityBinding
import com.hadietou.poulailler.databinding.ItemMortalityBinding
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

class MortalityActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityMortalityBinding
    private val calendar = Calendar.getInstance()
    private var selectedDateMs: Long = System.currentTimeMillis()
    private var userRole: String = "AGENT"
    private var userId: String? = null
    private var selectedBatchId: String? = null
    private val firebaseRepo = FirebaseRepository()
    private lateinit var adapter: MortalityAdapter
    
    private var allMortalities: List<Mortality> = emptyList()
    private var isShowingAll = false
    private var isBlocked = false
    
    private var isEggMenuExpanded = false
    private var isHealthMenuExpanded = true

    private val mortalityCauses = listOf(
        "Stress thermique (Chaleur)",
        "Newcastle (Pseudo-peste)",
        "Gumboro (IBD)",
        "Coccidiose",
        "Salmonellose / Typhose",
        "Coryza infectieux",
        "Grippe aviaire",
        "Prolapsus du cloaque",
        "Picage / Cannibalisme",
        "Prédation",
        "Inconnue",
        "Autre"
    )

    private val diseaseSymptoms = mapOf(
        "Stress thermique (Chaleur)" to listOf("Halètement (bec ouvert)", "Ailes écartées", "Soif intense", "Crête rouge foncé", "Prostration"),
        "Newcastle (Pseudo-peste)" to listOf("Cou tordu (torticollis)", "Diarrhée verdâtre", "Râles respiratoires", "Paralysie", "Forte mortalité"),
        "Gumboro (IBD)" to listOf("Diarrhée blanchâtre ou aqueuse", "Plumes ébouriffées", "Picage du cloaque", "Abattement profond"),
        "Coccidiose" to listOf("Diarrhée avec du sang ou orangée", "Crête pâle / blanche", "Ailes tombantes", "Amaigrissement rapide"),
        "Salmonellose / Typhose" to listOf("Diarrhée jaune soufre", "Yeux fermés", "Somnolence", "Mortalité subite"),
        "Coryza infectieux" to listOf("Yeux gonflés (sinusite)", "Écoulement au nez", "Éternuements", "Baisse de ponte"),
        "Grippe aviaire" to listOf("Crête et barbillons bleus ou noirs", "Œdème de la face", "Hémorragies sur les pattes", "Mortalité foudroyante"),
        "Prolapsus du cloaque" to listOf("Organe rouge qui sort du cloaque", "Picage par les autres"),
        "Picage / Cannibalisme" to listOf("Blessures sanglantes", "Plumes arrachées"),
        "Prédation" to listOf("Oiseau décapité", "Corps déchiqueté")
    )

    private val allSymptomsList: List<String> by lazy {
        diseaseSymptoms.values.flatten().distinct().sorted()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMortalityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        NetworkStatusMonitor.observe(this, binding.root)

        userRole = intent.getStringExtra("role") ?: "AGENT"
        userId = intent.getStringExtra("userIdString") ?: FirebaseAuth.getInstance().currentUser?.uid
        selectedBatchId = intent.getStringExtra("selectedBatchId")

        setupNavigation()
        setupRecyclerView()
        observeMortality()
        checkAccessStatus()

        binding.fabAddMortality.setOnClickListener {
            if (isBlocked) { showBlockingDialog(); return@setOnClickListener }
            showAddMortalityDialog()
        }

        binding.btnShowMore.setOnClickListener {
            isShowingAll = true
            refreshDisplay()
        }
    }

    private fun checkAccessStatus() {
        lifecycleScope.launch {
            val blocked = firebaseRepo.isFarmAccessBlocked()
            isBlocked = blocked
            if (blocked) {
                withContext(Dispatchers.Main) {
                    binding.fabAddMortality.visibility = View.GONE
                    showBlockingDialog()
                }
            }
        }
    }

    private fun showBlockingDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
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
        menu.findItem(R.id.nav_vaccines)?.isVisible = true
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

    private fun setupRecyclerView() {
        adapter = MortalityAdapter { mortality ->
            if (isBlocked) { showBlockingDialog(); return@MortalityAdapter }
            showEditMortalityDialog(mortality)
        }
        binding.rvMortalityHistory.layoutManager = LinearLayoutManager(this)
        binding.rvMortalityHistory.adapter = adapter
    }

    private fun observeMortality() {
        lifecycleScope.launch {
            firebaseRepo.getMortalityFlow().collectLatest { list ->
                allMortalities = if (selectedBatchId != null) {
                    list.filter { it.batchId == selectedBatchId }
                } else {
                    list
                }
                refreshDisplay()
            }
        }
    }

    private fun refreshDisplay() {
        val toDisplay = if (isShowingAll) allMortalities else allMortalities.take(10)
        adapter.submitList(toDisplay)
        binding.btnShowMore.visibility = if (!isShowingAll && allMortalities.size > 10) View.VISIBLE else View.GONE
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

    private fun showAddMortalityDialog() {
        val dialogBinding = DialogAddMortalityBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        dialogBinding.etMortalityDate.setText(sdf.format(Date()))
        selectedDateMs = System.currentTimeMillis()

        dialogBinding.etMortalityDate.setOnClickListener {
            DatePickerDialog(this, { _, year, month, dayOfMonth ->
                val tempCal = Calendar.getInstance()
                tempCal.set(year, month, dayOfMonth)
                selectedDateMs = tempCal.timeInMillis
                dialogBinding.etMortalityDate.setText(sdf.format(tempCal.time))
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        val causeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, mortalityCauses)
        dialogBinding.actvMortalityCause.setAdapter(causeAdapter)

        dialogBinding.btnHelpSymptoms.setOnClickListener {
            showSmartDiagnosisDialog { selectedCause ->
                dialogBinding.actvMortalityCause.setText(selectedCause, false)
            }
        }

        dialogBinding.btnSaveMortality.setOnClickListener {
            val countStr = dialogBinding.etMortalityInput.text.toString()
            val count = countStr.toIntOrNull() ?: 0
            val cause = dialogBinding.actvMortalityCause.text.toString()

            if (count <= 0) {
                Toast.makeText(this, "Veuillez saisir un nombre valide", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (cause.isEmpty()) {
                Toast.makeText(this, "Veuillez sélectionner une cause", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    firebaseRepo.addMortality(count, selectedDateMs, selectedBatchId, cause)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MortalityActivity, "Mortalité enregistrée", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        showAdviceDialog(cause)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MortalityActivity, e.message ?: "Erreur", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        dialog.show()
    }

    private fun showSmartDiagnosisDialog(onCauseSelected: (String) -> Unit) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("🔍 Cochez les symptômes observés :")

        val selectedSymptoms = BooleanArray(allSymptomsList.size)
        
        builder.setMultiChoiceItems(allSymptomsList.toTypedArray(), selectedSymptoms) { _, which, isChecked ->
            selectedSymptoms[which] = isChecked
        }

        builder.setPositiveButton("Analyser") { _, _ ->
            val checkedSymptoms = allSymptomsList.filterIndexed { index, _ -> selectedSymptoms[index] }
            if (checkedSymptoms.isEmpty()) {
                Toast.makeText(this, "Aucun symptôme sélectionné", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            // Simple logic: count matches
            val scores = diseaseSymptoms.mapValues { entry ->
                entry.value.count { it in checkedSymptoms }
            }

            val maxScore = scores.values.maxOrNull() ?: 0
            val likelyCauses = scores.filterValues { it == maxScore && it > 0 }.keys

            if (likelyCauses.isEmpty()) {
                Toast.makeText(this, "Impossible d'identifier une cause précise. Veuillez consulter un vétérinaire.", Toast.LENGTH_LONG).show()
            } else if (likelyCauses.size == 1) {
                val cause = likelyCauses.first()
                onCauseSelected(cause)
                Toast.makeText(this, "Cause probable : $cause", Toast.LENGTH_SHORT).show()
            } else {
                // Multiple possibilities
                AlertDialog.Builder(this)
                    .setTitle("Résultat de l'analyse")
                    .setMessage("Plusieurs causes sont possibles :\n" + likelyCauses.joinToString("\n") + "\n\nLaquelle correspond le mieux ?")
                    .setItems(likelyCauses.toTypedArray()) { _, which ->
                        onCauseSelected(likelyCauses.elementAt(which))
                    }
                    .show()
            }
        }
        
        builder.setNegativeButton("Annuler", null)
        builder.show()
    }

    private fun showEditMortalityDialog(mortality: Mortality) {
        val dialogBinding = DialogAddMortalityBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.etMortalityInput.setText(mortality.count.toString())
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        dialogBinding.etMortalityDate.setText(sdf.format(Date(mortality.date)))
        var editDateMs = mortality.date

        dialogBinding.etMortalityDate.setOnClickListener {
            val dCal = Calendar.getInstance().apply { timeInMillis = mortality.date }
            DatePickerDialog(this, { _, year, month, dayOfMonth ->
                val tempCal = Calendar.getInstance()
                tempCal.set(year, month, dayOfMonth)
                editDateMs = tempCal.timeInMillis
                dialogBinding.etMortalityDate.setText(sdf.format(tempCal.time))
            }, dCal.get(Calendar.YEAR), dCal.get(Calendar.MONTH), dCal.get(Calendar.DAY_OF_MONTH)).show()
        }

        val causeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, mortalityCauses)
        dialogBinding.actvMortalityCause.setAdapter(causeAdapter)
        dialogBinding.actvMortalityCause.setText(mortality.cause, false)

        dialogBinding.btnHelpSymptoms.setOnClickListener {
            showSmartDiagnosisDialog { selectedCause ->
                dialogBinding.actvMortalityCause.setText(selectedCause, false)
            }
        }

        val btnAdvice = Button(this).apply {
            text = "CONSEILS ET TRAITEMENTS"
            backgroundTintList = getColorStateList(R.color.primary)
            setTextColor(getColor(R.color.white))
            setOnClickListener { showAdviceDialog(dialogBinding.actvMortalityCause.text.toString()) }
        }
        (dialogBinding.root as ViewGroup).addView(btnAdvice, 3)

        if (userRole == "RESPONSABLE") {
            dialogBinding.btnSaveMortality.text = "MODIFIER"
            dialogBinding.btnSaveMortality.setOnClickListener {
                val count = dialogBinding.etMortalityInput.text.toString().toIntOrNull() ?: 0
                val cause = dialogBinding.actvMortalityCause.text.toString()

                if (count <= 0) {
                    Toast.makeText(this, "Nombre invalide", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val updated = mortality.copy(
                            count = count,
                            date = editDateMs,
                            cause = cause
                        )
                        firebaseRepo.updateMortality(updated)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MortalityActivity, "Mortalité modifiée", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MortalityActivity, e.message ?: "Erreur", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            val deleteButton = TextView(this).apply {
                text = "SUPPRIMER CET ENREGISTREMENT"
                setPadding(0, 48, 0, 0)
                setTextColor(getColor(R.color.error))
                gravity = android.view.Gravity.CENTER
                setOnClickListener {
                    AlertDialog.Builder(this@MortalityActivity)
                        .setTitle("Suppression")
                        .setMessage("Voulez-vous supprimer cette mortalité ?")
                        .setPositiveButton("Supprimer") { _, _ ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                try {
                                    if (mortality.firestoreId != null) {
                                        firebaseRepo.deleteMortality(mortality.firestoreId)
                                    }
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(this@MortalityActivity, "Supprimé", Toast.LENGTH_SHORT).show()
                                        dialog.dismiss()
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(this@MortalityActivity, e.message ?: "Erreur", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                        .setNegativeButton("Annuler", null)
                        .show()
                }
            }
            (dialogBinding.root as ViewGroup).addView(deleteButton)
        } else {
            dialogBinding.etMortalityInput.isEnabled = false
            dialogBinding.etMortalityDate.isEnabled = false
            dialogBinding.actvMortalityCause.isEnabled = false
            dialogBinding.btnSaveMortality.visibility = View.GONE
        }

        dialog.show()
    }

    private fun showAdviceDialog(cause: String) {
        if (cause.isEmpty() || cause == "Inconnue" || cause == "Autre") return

        val advice = when (cause) {
            "Stress thermique (Chaleur)" -> """
                🌿 PRÉVENTIF : 
                - Isoler le toit (paille ou peinture blanche).
                - Installer des brasseurs d'air.
                - Eau fraîche à volonté (ajouter des glaçons si possible).
                - Vitamine C ou Électrolytes dans l'eau.
                
                💊 CURATIF : 
                - Diminuer la densité d'oiseaux.
                - Distribuer l'aliment aux heures fraîches.
            """.trimIndent()
            
            "Newcastle (Pseudo-peste)" -> """
                🌿 PRÉVENTIF : 
                - VACCINATION OBLIGATOIRE (J7, J21, rappel tous les 3 mois).
                - Biosécurité stricte (pédiluves, pas de visiteurs).
                
                💊 CURATIF : 
                - Aucun traitement (virale).
                - Isoler les malades et désinfecter.
            """.trimIndent()

            "Gumboro (IBD)" -> """
                🌿 PRÉVENTIF : 
                - Vaccination (J10, J18).
                - Désinfection totale entre les bandes.
                
                💊 CURATIF : 
                - Vitamines (complexe B).
                - Protecteurs rénaux et hépatiques.
                - Antibiotiques si infections secondaires.
            """.trimIndent()

            "Coccidiose" -> """
                🌿 PRÉVENTIF : 
                - Garder la litière sèche et aérée.
                - Éviter les fuites d'abreuvoirs.
                
                💊 CURATIF : 
                - Amprolium ou Sulfamides dans l'eau (3-5 jours).
                - Vitamine K pour stopper les hémorragies.
            """.trimIndent()

            "Salmonellose / Typhose" -> """
                🌿 PRÉVENTIF : 
                - Hygiène de l'eau (javellisation).
                - Dératisation stricte du bâtiment.
                
                💊 CURATIF : 
                - Antibiotiques (Tétracyclines, Colistine) après avis vétérinaire.
            """.trimIndent()

            "Coryza infectieux" -> """
                🌿 PRÉVENTIF : 
                - Éviter l'humidité et les courants d'air.
                - Vaccination disponible pour zones à risque.
                
                💊 CURATIF : 
                - Tylosine ou Érythromycine dans l'eau.
            """.trimIndent()

            "Prolapsus du cloaque" -> """
                🌿 PRÉVENTIF : 
                - Éviter le surpoids des poules (ration équilibrée).
                - Ne pas stimuler la ponte trop tôt par la lumière.
                - Apport suffisant en calcium et phosphore.
                
                💊 CURATIF : 
                - Isoler immédiatement la poule (pour éviter le picage).
                - Nettoyer l'organe avec un antiseptique doux.
                - Si possible, remettre délicatement en place avec du lubrifiant.
                - Si grave ou récidivant, la réforme (abattage) est recommandée.
            """.trimIndent()

            "Picage / Cannibalisme" -> """
                🌿 PRÉVENTIF : 
                - Épointage du bec (débecquage).
                - Réduire l'intensité lumineuse.
                - Apport en minéraux (sel) et fibres.
                
                💊 CURATIF : 
                - Isoler les oiseaux blessés.
                - Appliquer du goudron de Norvège ou spray bleu.
            """.trimIndent()

            else -> "Veuillez consulter un technicien avicole ou un vétérinaire pour un diagnostic précis."
        }

        AlertDialog.Builder(this)
            .setTitle("💡 Conseils pour : $cause")
            .setMessage(advice)
            .setPositiveButton("OK", null)
            .show()
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
                intent.putExtra("selectedBatchId", selectedBatchId)
                startActivity(intent)
            }
            R.id.nav_vaccines -> {
                val intent = Intent(this, VaccineActivity::class.java)
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
            R.id.nav_mortality -> {}
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
            val blocked = firebaseRepo.isFarmAccessBlocked()
            isBlocked = blocked
        }
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    class MortalityAdapter(private val onItemClick: (Mortality) -> Unit) : RecyclerView.Adapter<MortalityAdapter.ViewHolder>() {
        private var items = listOf<Mortality>()

        fun submitList(list: List<Mortality>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemMortalityBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.bind(item)
            holder.itemView.setOnClickListener { onItemClick(item) }
        }

        override fun getItemCount() = items.size

        class ViewHolder(private val binding: ItemMortalityBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(item: Mortality) {
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                binding.tvDate.text = sdf.format(Date(item.date))
                binding.tvCount.text = "${item.count} Poules"
                if (!item.cause.isNullOrEmpty()) {
                    binding.tvCause.text = item.cause
                    binding.tvCause.visibility = View.VISIBLE
                } else {
                    binding.tvCause.visibility = View.GONE
                }
            }
        }
    }
}
