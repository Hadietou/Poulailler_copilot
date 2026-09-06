package com.hadietou.poulailler.ui

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.hadietou.poulailler.R
import com.hadietou.poulailler.databinding.ActivityFeedConsumptionBinding
import java.text.NumberFormat
import java.util.Locale

/**
 * Écran "Suivi de la consommation" accessible depuis la case Alimentation du dashboard.
 * Réutilise le [DashboardViewModel] (mêmes calculs de stock/autonomie/IC que le dashboard)
 * pour éviter de dupliquer la logique de calcul de la ration.
 */
class FeedConsumptionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFeedConsumptionBinding
    private val viewModel: DashboardViewModel by viewModels()
    private var selectedBatchId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFeedConsumptionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        selectedBatchId = intent.getStringExtra("selectedBatchId")

        binding.toolbar.setNavigationOnClickListener { finish() }

        observeViewModel()
        viewModel.loadData()
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

        val numberFormat = NumberFormat.getInstance(Locale.getDefault())

        viewModel.currentStockKg.observe(this) { stock ->
            binding.tvFeedStockValue.text = stock.toInt().toString()
            binding.tvFeedTotalRemaining.text = getString(R.string.kg_unit, stock.toInt())
        }

        viewModel.feedAutonomyDays.observe(this) { days ->
            binding.tvFeedAutonomySubtitle.text = "Autonomie : $days jours"
        }

        viewModel.dailyConsumptionPerHenG.observe(this) { grams ->
            binding.tvFeedPerHenValue.text = grams.toInt().toString()
        }

        viewModel.dailyConsumptionTotalKg.observe(this) { totalKg ->
            binding.tvFeedTotalDailySubtitle.text = String.format(Locale.getDefault(), "Troupeau : %.1f kg/jour", totalKg)
        }

        viewModel.feedConversionRatio.observe(this) { ic ->
            binding.tvFeedICValue.text = String.format(Locale.getDefault(), "%.2f", ic)
        }

        viewModel.totalFeedCost.observe(this) { cost ->
            val curr = viewModel.farmInfo.value?.currency ?: "MRU"
            binding.tvFeedCostValue.text = "≈ ${numberFormat.format(cost.toInt())} $curr"
        }

        viewModel.totalFeedPurchasedKg.observe(this) { purchased ->
            binding.tvFeedTotalPurchased.text = getString(R.string.kg_unit, purchased.toInt())
        }

        viewModel.totalFeedConsumedKg.observe(this) { consumed ->
            binding.tvFeedTotalConsumed.text = getString(R.string.kg_unit, consumed.toInt())
        }
    }
}
