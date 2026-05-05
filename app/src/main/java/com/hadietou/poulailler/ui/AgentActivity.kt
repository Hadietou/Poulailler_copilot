package com.hadietou.poulailler.ui

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
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
import com.hadietou.poulailler.data.AppDatabase
import com.hadietou.poulailler.data.EggEntry
import com.hadietou.poulailler.databinding.ActivityAgentBinding
import com.hadietou.poulailler.databinding.DialogAddCollectionBinding
import com.hadietou.poulailler.databinding.ItemCollectionBinding
import com.hadietou.poulailler.repository.FirebaseRepository
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class AgentActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityAgentBinding
    private val viewModel: AgentViewModel by viewModels()
    private val calendar = Calendar.getInstance()
    private var selectedDateMs: Long = System.currentTimeMillis()
    private var userId: String? = null
    private var userRole: String = "AGENT"
    private var selectedBatchId: String? = null
    private val firebaseRepo = FirebaseRepository()
    private lateinit var adapter: CollectionAdapter
    
    private var allEntries: List<EggEntry> = emptyList()
    private var isShowingAll = false
    private var isBlocked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAgentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userId = intent.getStringExtra("userIdString") ?: FirebaseAuth.getInstance().currentUser?.uid
        userRole = intent.getStringExtra("role") ?: "AGENT"
        selectedBatchId = intent.getStringExtra("selectedBatchId")

        setupNavigation()
        setupRecyclerView()
        observeCollections()

        binding.fabAddCollection.setOnClickListener {
            if (isBlocked) { showBlockingDialog(); return@setOnClickListener }
            showAddCollectionDialog()
        }

        binding.btnShowMore.setOnClickListener {
            isShowingAll = true
            refreshDisplay()
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
        menu.findItem(R.id.nav_users)?.isVisible = userRole == "RESPONSABLE"
        menu.findItem(R.id.nav_expenses)?.isVisible = userRole == "RESPONSABLE"
        menu.findItem(R.id.nav_vaccines)?.isVisible = true
        menu.findItem(R.id.nav_batches)?.isVisible = userRole == "RESPONSABLE"

        updateNavHeader()
    }

    private fun setupRecyclerView() {
        adapter = CollectionAdapter { entry ->
            if (isBlocked) { showBlockingDialog(); return@CollectionAdapter }
            if (userRole == "RESPONSABLE") {
                showEditCollectionDialog(entry)
            }
        }
        binding.rvCollectionHistory.layoutManager = LinearLayoutManager(this)
        binding.rvCollectionHistory.adapter = adapter
    }

    private fun observeCollections() {
        viewModel.isAccessBlocked.observe(this) { blocked ->
            isBlocked = blocked
            if (blocked) {
                binding.fabAddCollection.visibility = View.GONE
                showBlockingDialog()
            }
        }

        viewModel.entries.observe(this) { list ->
            allEntries = if (selectedBatchId != null) {
                list.filter { it.batchId == selectedBatchId }
            } else {
                list
            }
            refreshDisplay()
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

    private fun refreshDisplay() {
        val toDisplay = if (isShowingAll) allEntries else allEntries.take(10)
        adapter.submitList(toDisplay)
        binding.btnShowMore.visibility = if (!isShowingAll && allEntries.size > 10) View.VISIBLE else View.GONE
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

    private fun showAddCollectionDialog() {
        val dialogBinding = DialogAddCollectionBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        dialogBinding.btnSelectDate.text = "Date: ${sdf.format(Date())}"
        selectedDateMs = System.currentTimeMillis()

        dialogBinding.btnSelectDate.setOnClickListener {
            DatePickerDialog(this, { _, year, month, dayOfMonth ->
                val tempCal = Calendar.getInstance()
                tempCal.set(year, month, dayOfMonth)
                selectedDateMs = tempCal.timeInMillis
                dialogBinding.btnSelectDate.text = "Date: ${sdf.format(tempCal.time)}"
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        dialogBinding.btnAddEntry.setOnClickListener {
            val count = dialogBinding.etEggsCount.text.toString().toIntOrNull() ?: 0
            val broken = dialogBinding.etBrokenEggsCount.text.toString().toIntOrNull() ?: 0
            val remarks = dialogBinding.etRemarks.text.toString()

            if (count == 0 && broken == 0) {
                Toast.makeText(this, "Veuillez saisir au moins une donnée", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.addEntry(
                userId = userId ?: "",
                date = selectedDateMs,
                eggs = count,
                broken = broken,
                remarks = remarks,
                batchId = selectedBatchId,
                onDone = {
                    Toast.makeText(this@AgentActivity, "Collecte enregistrée", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            )
        }

        dialog.show()
    }

    private fun showEditCollectionDialog(entry: EggEntry) {
        val dialogBinding = DialogAddCollectionBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.etEggsCount.setText(entry.eggsCount.toString())
        dialogBinding.etBrokenEggsCount.setText(entry.brokenEggsCount.toString())
        dialogBinding.etRemarks.setText(entry.remarks ?: "")
        
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        dialogBinding.btnSelectDate.text = "Date: ${sdf.format(Date(entry.date))}"
        var editDateMs = entry.date

        dialogBinding.btnSelectDate.setOnClickListener {
            val dCal = Calendar.getInstance().apply { timeInMillis = entry.date }
            DatePickerDialog(this, { _, year, month, dayOfMonth ->
                val tempCal = Calendar.getInstance()
                tempCal.set(year, month, dayOfMonth)
                editDateMs = tempCal.timeInMillis
                dialogBinding.btnSelectDate.text = "Date: ${sdf.format(tempCal.time)}"
            }, dCal.get(Calendar.YEAR), dCal.get(Calendar.MONTH), dCal.get(Calendar.DAY_OF_MONTH)).show()
        }

        dialogBinding.btnAddEntry.text = "MODIFIER"
        dialogBinding.btnAddEntry.setOnClickListener {
            val count = dialogBinding.etEggsCount.text.toString().toIntOrNull() ?: 0
            val broken = dialogBinding.etBrokenEggsCount.text.toString().toIntOrNull() ?: 0
            val remarks = dialogBinding.etRemarks.text.toString()

            if (count == 0 && broken == 0) {
                Toast.makeText(this, "Champs vides", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val updatedEntry = entry.copy(
                        date = editDateMs,
                        eggsCount = count,
                        brokenEggsCount = broken,
                        remarks = remarks
                    )
                    firebaseRepo.updateEggEntry(updatedEntry)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@AgentActivity, "Collecte modifiée", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@AgentActivity, e.message ?: "Erreur", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        val deleteButton = TextView(this).apply {
            text = "SUPPRIMER CETTE COLLECTE"
            setPadding(0, 32, 0, 0)
            setTextColor(getColor(R.color.error))
            gravity = android.view.Gravity.CENTER
            setOnClickListener {
                AlertDialog.Builder(this@AgentActivity)
                    .setTitle("Suppression")
                    .setMessage("Voulez-vous vraiment supprimer cette collecte ?")
                    .setPositiveButton("Supprimer") { _, _ ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                if (entry.firestoreId != null) {
                                    firebaseRepo.deleteEggEntry(entry.firestoreId)
                                }
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@AgentActivity, "Collecte supprimée", Toast.LENGTH_SHORT).show()
                                    dialog.dismiss()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@AgentActivity, e.message ?: "Erreur", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    .setNegativeButton("Annuler", null)
                    .show()
            }
        }
        (dialogBinding.root as ViewGroup).addView(deleteButton)

        dialog.show()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Navigation is allowed even if blocked for visualization
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
            R.id.nav_collect -> {}
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
            R.id.nav_mortality -> {
                val intent = Intent(this, MortalityActivity::class.java)
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

    override fun onResume() {
        super.onResume()
        updateNavHeader()
        viewModel.checkAccessStatus()
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    class CollectionAdapter(private val onItemClick: (EggEntry) -> Unit) : RecyclerView.Adapter<CollectionAdapter.ViewHolder>() {
        private var items = listOf<EggEntry>()

        fun submitList(list: List<EggEntry>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemCollectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.bind(item)
            holder.itemView.setOnClickListener { onItemClick(item) }
        }

        override fun getItemCount() = items.size

        class ViewHolder(private val binding: ItemCollectionBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(item: EggEntry) {
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                binding.tvDate.text = sdf.format(Date(item.date))
                binding.tvEggs.text = "${item.eggsCount} œufs"
                binding.tvBroken.text = "${item.brokenEggsCount} cassés"
            }
        }
    }
}
