package com.example.poulailler_copilot.ui

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import com.example.poulailler_copilot.R
import com.example.poulailler_copilot.databinding.ActivityDashboardBinding
import com.google.android.material.navigation.NavigationView

class DashboardActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityDashboardBinding
    private var userRole: String = "AGENT"
    private var userId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userRole = intent.getStringExtra("role") ?: "AGENT"
        userId = intent.getLongExtra("userId", -1)

        setupNavigation()
        updateUIBasedOnRole()
        loadDashboardData()
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
    }

    private fun updateUIBasedOnRole() {
        val menu = binding.navigationView.menu
        
        // Seul le Responsable voit la gestion des agents, les dépenses et les vaccins
        menu.findItem(R.id.nav_users).isVisible = userRole == "RESPONSABLE"
        menu.findItem(R.id.nav_expenses).isVisible = userRole == "RESPONSABLE"
        menu.findItem(R.id.nav_vaccines).isVisible = userRole == "RESPONSABLE"
        
        // Tout le monde voit le Dashboard, la collecte et les ventes
        menu.findItem(R.id.nav_dashboard).isVisible = true
        menu.findItem(R.id.nav_collect).isVisible = true
        menu.findItem(R.id.nav_sales).isVisible = true
    }

    private fun loadDashboardData() {
        binding.tvHensCount.text = "1500"
        binding.tvHensAge.text = "24"
        binding.tvTodayEggs.text = "1420 œufs"
        binding.tvSalesRevenue.text = "450.00 $"
        binding.tvTotalExpenses.text = "120.00 $"
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_dashboard -> { }
            R.id.nav_users -> {
                startActivity(Intent(this, ResponsableActivity::class.java))
            }
            R.id.nav_collect -> {
                val intent = Intent(this, AgentActivity::class.java)
                intent.putExtra("userId", userId)
                startActivity(intent)
            }
            R.id.nav_vaccines -> {
                startActivity(Intent(this, VaccineActivity::class.java))
            }
            R.id.nav_expenses -> {
                startActivity(Intent(this, ExpensesActivity::class.java))
            }
            R.id.nav_sales -> {
                startActivity(Intent(this, SalesActivity::class.java))
            }
            R.id.nav_logout -> {
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
