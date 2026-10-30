package com.hadietou.poulailler.ui

import com.hadietou.poulailler.util.NumberFormats
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.hadietou.poulailler.R
import com.hadietou.poulailler.data.CategoryExpense
import com.hadietou.poulailler.databinding.FragmentFinanceOverviewBinding
import com.hadietou.poulailler.repository.FirebaseRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.NumberFormat

/** Onglet « Situation globale » de la page Finances : bénéfice, entrées, dépenses depuis le début. */
class FinanceOverviewFragment : Fragment() {

    private var _binding: FragmentFinanceOverviewBinding? = null
    private val binding get() = _binding!!
    private val repository = FirebaseRepository()
    private var currency = "MRU"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFinanceOverviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = requireArguments()
        val batchId = args.getString("selectedBatchId")

        binding.btnAddSale.setOnClickListener { open(SalesActivity::class.java) }
        binding.btnAddExpense.setOnClickListener { open(ExpensesActivity::class.java) }

        viewLifecycleOwner.lifecycleScope.launch {
            currency = repository.getFarmInfo()?.currency ?: "MRU"
            combine(repository.getSalesFlow(), repository.getExpensesFlow()) { sales, expenses ->
                sales.filter { batchId == null || it.batchId == batchId } to expenses.filter { batchId == null || it.batchId == batchId }
            }.collectLatest { (sales, expenses) ->
                val revenue = sales.sumOf { it.totalPrice }
                val spent = expenses.sumOf { it.amount }
                val profit = revenue - spent
                val format = NumberFormats.plain()

                binding.tvTotalRevenue.text = "${format.format(revenue)} $currency"
                binding.tvTotalExpenses.text = "${format.format(spent)} $currency"
                binding.tvNetProfit.text = "${format.format(profit)} $currency"
                binding.tvNetProfit.setTextColor(if (profit < 0) requireContext().getColor(R.color.error) else requireContext().getColor(R.color.text_primary))

                DashboardChartRenderer.renderExpenses(
                    binding.expensesBarChart,
                    requireContext(),
                    expenses.groupBy { it.category }.map { (category, items) -> CategoryExpense(category, items.sumOf { it.amount }) }
                )
            }
        }
    }

    private fun open(target: Class<*>) {
        val args = requireArguments()
        startActivity(
            Intent(requireContext(), target)
                .putExtra("role", args.getString("role"))
                .putExtra("userIdString", args.getString("userIdString"))
                .putExtra("selectedBatchId", args.getString("selectedBatchId"))
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
