package com.hadietou.poulailler.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.hadietou.poulailler.databinding.ActivityFinanceBinding

/**
 * Page Finances : regroupe la situation financière globale, les ventes et recettes, et le suivi des
 * dépenses. Chaque onglet est un fragment ; la saisie d'une vente ou d'une dépense reste dans
 * [SalesActivity] et [ExpensesActivity], accessibles depuis l'onglet « Situation globale ».
 */
class FinanceActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TAB = "financeTab"
        const val TAB_OVERVIEW = 0
        const val TAB_SALES = 1
        const val TAB_EXPENSES = 2
        private val TAB_TITLES = listOf("Situation globale", "Ventes et recettes", "Dépenses")
    }

    private lateinit var binding: ActivityFinanceBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFinanceBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val batchId = intent.getStringExtra("selectedBatchId")
        val role = intent.getStringExtra("role")
        val userId = intent.getStringExtra("userIdString")

        binding.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = TAB_TITLES.size

            override fun createFragment(position: Int): Fragment = when (position) {
                TAB_SALES -> statsFragment("SALES", batchId)
                TAB_EXPENSES -> statsFragment("EXPENSES", batchId)
                else -> FinanceOverviewFragment().apply {
                    arguments = bundleOf("selectedBatchId" to batchId, "role" to role, "userIdString" to userId)
                }
            }
        }
        // Les onglets de statistiques recalculent leurs graphiques : on garde les trois en mémoire.
        binding.viewPager.offscreenPageLimit = TAB_TITLES.size
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position -> tab.text = TAB_TITLES[position] }.attach()

        binding.viewPager.setCurrentItem(intent.getIntExtra(EXTRA_TAB, TAB_OVERVIEW).coerceIn(0, TAB_TITLES.lastIndex), false)
    }

    private fun statsFragment(type: String, batchId: String?) = MonthlyStatsFragment().apply {
        arguments = bundleOf("statsType" to type, "selectedBatchId" to batchId, "embedded" to true)
    }
}
