package com.hadietou.poulailler.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import com.hadietou.poulailler.R

/**
 * Page de statistiques mensuelles (production, mortalité). Le contenu est dans [MonthlyStatsFragment].
 * Les ventes et les dépenses sont regroupées avec la situation financière dans [FinanceActivity] :
 * toute demande SALES / REVENUE / EXPENSES y est redirigée vers l'onglet correspondant.
 */
class MonthlyStatsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val requested = intent.getStringExtra("statsType") ?: "COLLECTION"
        val financeTab = when (requested) {
            "SALES", "REVENUE" -> FinanceActivity.TAB_SALES
            "EXPENSES" -> FinanceActivity.TAB_EXPENSES
            else -> null
        }
        if (financeTab != null) {
            startActivity(
                Intent(this, FinanceActivity::class.java)
                    .putExtra(FinanceActivity.EXTRA_TAB, financeTab)
                    .putExtra("selectedBatchId", intent.getStringExtra("selectedBatchId"))
                    .putExtra("role", intent.getStringExtra("role"))
                    .putExtra("userIdString", intent.getStringExtra("userIdString"))
            )
            finish()
            return
        }

        setContentView(R.layout.activity_stats_host)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(
                    R.id.statsContainer,
                    MonthlyStatsFragment().apply {
                        arguments = bundleOf(
                            "statsType" to requested,
                            "selectedBatchId" to intent.getStringExtra("selectedBatchId")
                        )
                    }
                )
                .commit()
        }
    }
}
