package com.example.poulailler_copilot.ui

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
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.poulailler_copilot.R
import com.example.poulailler_copilot.data.AppDatabase
import com.example.poulailler_copilot.data.Mortality
import com.example.poulailler_copilot.databinding.ActivityMortalityBinding
import com.example.poulailler_copilot.databinding.DialogAddMortalityBinding
import com.example.poulailler_copilot.databinding.ItemMortalityBinding
import com.example.poulailler_copilot.repository.FirebaseRepository
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class MortalityActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityMortalityBinding
    private val calendar = Calendar.getInstance()
    private var selectedDateMs: Long = System.currentTimeMillis()
    private var userRole: String = "AGENT"
    private var userId: String? = null
    private val firebaseRepo = FirebaseRepository()
    private lateinit var adapter: MortalityAdapter
    
    private var allMortalities: List<Mortality> = emptyList()
    private var isShowingAll = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMortalityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userRole = intent.getStringExtra("role") ?: "AGENT"
        userId = intent.getStringExtra("userIdString") ?: FirebaseAuth.getInstance().currentUser?.uid

        setupNavigation()
        setupRecyclerView()
        observeMortality()

        binding.fabAddMortality.setOnClickListener {
            showAddMortalityDialog()
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
        menu.findItem(R.id.nav_users).isVisible = userRole == "RESPONSABLE"
        menu.findItem(R.id.nav_expenses).isVisible = userRole == "RESPONSABLE"
        menu.findItem(R.id.nav_vaccines).isVisible = userRole == "RESPONSABLE"
        menu.findItem(R.id.nav_farm_info).isVisible = userRole == "RESPONSABLE"

        updateNavHeader()
    }

    private fun setupRecyclerView() {
        adapter = MortalityAdapter { mortality ->
            if (userRole == "RESPONSABLE") {
                showEditMortalityDialog(mortality)
            }
        }
        binding.rvMortalityHistory.layoutManager = LinearLayoutManager(this)
        binding.rvMortalityHistory.adapter = adapter
    }

    private fun observeMortality() {
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(this@MortalityActivity)
            db.mortalityDao().getAllMortality().collectLatest { list ->
                allMortalities = list
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

        lifecycleScope.launch {
            val uid = userId ?: FirebaseAuth.getInstance().currentUser?.uid
            if (uid != null) {
                val profile = firebaseRepo.getUserProfile(uid)
                withContext(Dispatchers.Main) {
                    tvUsername.text = profile?.username ?: "Utilisateur"
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

        dialogBinding.btnSaveMortality.setOnClickListener {
            val countStr = dialogBinding.etMortalityInput.text.toString()
            val count = countStr.toIntOrNull() ?: 0

            if (count <= 0) {
                Toast.makeText(this, "Veuillez saisir un nombre valide", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch(Dispatchers.IO) {
                val db = AppDatabase.getInstance(this@MortalityActivity)
                val mortality = Mortality(count = count, date = selectedDateMs)
                db.mortalityDao().insert(mortality)
                firebaseRepo.addMortality(count, selectedDateMs)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MortalityActivity, "Mortalité enregistrée", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }

    private fun showEditMortalityDialog(mortality: Mortality) {
        val dialogBinding = DialogAddMortalityBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        // Pre-fill
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

        dialogBinding.btnSaveMortality.text = "MODIFIER"
        dialogBinding.btnSaveMortality.setOnClickListener {
            val count = dialogBinding.etMortalityInput.text.toString().toIntOrNull() ?: 0
            if (count <= 0) {
                Toast.makeText(this, "Nombre invalide", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch(Dispatchers.IO) {
                val updated = mortality.copy(
                    count = count,
                    date = editDateMs
                )
                AppDatabase.getInstance(this@MortalityActivity).mortalityDao().update(updated)
                firebaseRepo.updateMortality(updated)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MortalityActivity, "Mortalité modifiée", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
        }

        val deleteButton = TextView(this).apply {
            text = "SUPPRIMER CET ENREGISTREMENT"
            setPadding(0, 32, 0, 0)
            setTextColor(getColor(R.color.error))
            gravity = android.view.Gravity.CENTER
            setOnClickListener {
                AlertDialog.Builder(this@MortalityActivity)
                    .setTitle("Suppression")
                    .setMessage("Voulez-vous supprimer cette mortalité ?")
                    .setPositiveButton("Supprimer") { _, _ ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            AppDatabase.getInstance(this@MortalityActivity).mortalityDao().delete(mortality)
                            if (mortality.firestoreId != null) {
                                firebaseRepo.deleteMortality(mortality.firestoreId)
                            }
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@MortalityActivity, "Supprimé", Toast.LENGTH_SHORT).show()
                                dialog.dismiss()
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
        when (item.itemId) {
            R.id.nav_dashboard -> {
                val intent = Intent(this, DashboardActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                startActivity(intent)
                finish()
            }
            R.id.nav_users -> {
                val intent = Intent(this, ResponsableActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                startActivity(intent)
            }
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
                intent.putExtra("userIdString", userId)
                intent.putExtra("role", userRole)
                startActivity(intent)
            }
            R.id.nav_mortality -> {}
            R.id.nav_logout -> {
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
            }
        }
    }
}
