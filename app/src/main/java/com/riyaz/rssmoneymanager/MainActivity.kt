package com.riyaz.rssmoneymanager

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.riyaz.rssmoneymanager.databinding.ActivityMainBinding
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private var _binding: ActivityMainBinding? = null
    private val binding get() = checkNotNull(_binding)
    private var balanceMinor = 0L
    private var incomeMinor = 0L
    private var expenseMinor = 0L
    private val recentTransactions = mutableListOf<Transaction>()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.addTransactionButton.setOnClickListener { showTransactionDialog() }
        renderDashboard()
    }

    private fun showTransactionDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 0)
        }
        val type = TextView(this).apply {
            text = "Expense"
            textSize = 16f
            setPadding(0, 8, 0, 12)
            isClickable = true
            setOnClickListener { text = if (text == "Expense") "Income" else "Expense" }
        }
        val count = field("Count", "1", InputType.TYPE_CLASS_NUMBER)
        val amount = field("Amount", "0.00", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val total = TextView(this).apply { textSize = 20f; setPadding(0, 12, 0, 16); text = "Total: 0.00" }
        val description = field("Description", "", InputType.TYPE_CLASS_TEXT)
        val category = field("Category", "House Expenses", InputType.TYPE_CLASS_TEXT)
        val wallet = field("Wallet", "Cash", InputType.TYPE_CLASS_TEXT)
        val memo = field("Memo (optional)", "", InputType.TYPE_CLASS_TEXT)

        fun recalculate() {
            val c = count.text.toString().toLongOrNull()?.coerceAtLeast(1L) ?: 1L
            val unit = parseMinor(amount.text.toString())
            total.text = "Total: ${formatMinor(c * unit)}"
        }
        count.setOnFocusChangeListener { _, _ -> recalculate() }
        amount.setOnFocusChangeListener { _, _ -> recalculate() }
        count.setOnKeyListener { _, _, _ -> recalculate(); false }
        amount.setOnKeyListener { _, _, _ -> recalculate(); false }

        container.addView(type); container.addView(count); container.addView(amount); container.addView(total)
        container.addView(description); container.addView(category); container.addView(wallet); container.addView(memo)

        val dialog = MaterialAlertDialogBuilder(this).setTitle("Add Transaction").setView(container)
            .setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val c = count.text.toString().toLongOrNull()?.coerceAtLeast(1L) ?: 1L
                val unit = parseMinor(amount.text.toString())
                val calculatedTotal = try { Math.multiplyExact(c, unit) } catch (_: ArithmeticException) { Long.MAX_VALUE }
                val desc = description.text.toString().trim()
                if (unit <= 0L || desc.isEmpty() || calculatedTotal == Long.MAX_VALUE) {
                    Snackbar.make(binding.root, "Enter a valid amount and description", Snackbar.LENGTH_SHORT).show(); return@setOnClickListener
                }
                val transaction = Transaction(type.text.toString(), c, unit, calculatedTotal, desc,
                    category.text.toString().trim().ifEmpty { "Uncategorized" },
                    wallet.text.toString().trim().ifEmpty { "Cash" }, memo.text.toString().trim())
                recentTransactions.add(0, transaction)
                if (transaction.type == "Income") { incomeMinor += calculatedTotal; balanceMinor += calculatedTotal }
                else { expenseMinor += calculatedTotal; balanceMinor -= calculatedTotal }
                renderDashboard(); dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun renderDashboard() {
        binding.balanceText.text = formatMinor(balanceMinor)
        binding.incomeText.text = formatMinor(incomeMinor)
        binding.expenseText.text = formatMinor(expenseMinor)
        binding.transactionCount.text = "${recentTransactions.size} transactions"
        binding.recentContainer.removeAllViews()
        binding.emptyText.visibility = if (recentTransactions.isEmpty()) View.VISIBLE else View.GONE
        recentTransactions.take(8).forEach { transaction ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 14, 16, 14) }
            row.addView(TextView(this).apply { text = transaction.description; textSize = 16f })
            row.addView(TextView(this).apply {
                text = "${transaction.count} × ${formatMinor(transaction.unitMinor)} × ${formatMinor(transaction.totalMinor)}  •  ${transaction.category}"
                textSize = 13f
            })
            row.addView(TextView(this).apply { text = transaction.wallet; textSize = 12f })
            binding.recentContainer.addView(row)
        }
    }

    private fun field(hint: String, value: String, inputType: Int) = EditText(this).apply {
        this.hint = hint; setText(value); this.inputType = inputType; textSize = 16f; setSingleLine(true)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 }
    }

    private fun parseMinor(value: String): Long = try {
        BigDecimal(value.trim().ifEmpty { "0" }).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
    } catch (_: Exception) { 0L }

    private fun formatMinor(value: Long): String = NumberFormat.getCurrencyInstance(Locale.getDefault()).format(BigDecimal.valueOf(value, 2))

    override fun onDestroy() { _binding = null; super.onDestroy() }

    private data class Transaction(val type: String, val count: Long, val unitMinor: Long, val totalMinor: Long,
                                   val description: String, val category: String, val wallet: String, val memo: String)
}
