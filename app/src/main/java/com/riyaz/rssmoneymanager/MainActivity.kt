package com.riyaz.rssmoneymanager

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private var _binding: ActivityMainBinding? = null
    private val binding get() = checkNotNull(_binding)
    private var balanceMinor = 0L
    private var incomeMinor = 0L
    private var expenseMinor = 0L
    private val recentTransactions = mutableListOf<Transaction>()
    private val categories = listOf("House Expenses", "Food", "Transport", "Bills", "Shopping", "Salary", "Other")
    private val wallets = listOf("Cash", "Bank", "Card", "Savings", "Other")

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupMagicNavigation()
        binding.addTransactionButton.setOnClickListener { showTransactionDialog() }
        renderDashboard()
        renderTransactions()
    }

    private fun setupMagicNavigation() {
        MagicNavigation.setup(
            binding.navActiveIndicator,
            binding.navHome,
            binding.navTransactions,
            binding.addTransactionButton,
            binding.navAccounts,
            binding.navMore
        ) { index ->
            when (index) {
                0 -> showScreen(binding.homeScreen)
                1 -> showScreen(binding.transactionsScreen)
                2 -> showTransactionDialog()
                3 -> showScreen(binding.accountsScreen)
                4 -> showScreen(binding.moreScreen)
            }
        }
    }

    private fun showScreen(screen: View) {
        val screens = listOf(binding.homeScreen, binding.transactionsScreen, binding.accountsScreen, binding.moreScreen)
        screens.forEach { if (it !== screen) it.visibility = View.GONE }
        screen.visibility = View.VISIBLE
        screen.alpha = 0f
        screen.translationY = 22f
        screen.animate().alpha(1f).translationY(0f).setDuration(240L).start()
    }

    private fun showTransactionDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 0)
        }

        var selectedType = "Expense"
        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 4, 0, 12)
        }
        val incomeTab = entryTab("Income")
        val expenseTab = entryTab("Expenses")
        val transferTab = entryTab("Transfer")
        tabs.addView(incomeTab, LinearLayout.LayoutParams(0, -2, 1f))
        tabs.addView(expenseTab, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = 6; marginEnd = 6 })
        tabs.addView(transferTab, LinearLayout.LayoutParams(0, -2, 1f))

        val dateTime = TextView(this).apply {
            text = formatDateTime(System.currentTimeMillis())
            textSize = 15f
            setPadding(0, 8, 0, 8)
            isClickable = true
        }
        dateTime.setOnClickListener { pickDateTime(dateTime) }
        val recurring = TextView(this).apply {
            text = "↻  Recurring: Off"
            textSize = 15f
            setPadding(0, 8, 0, 12)
            isClickable = true
        }
        recurring.setOnClickListener {
            recurring.text = if (recurring.text.toString().endsWith("Off")) "↻  Recurring: On" else "↻  Recurring: Off"
        }

        val count = field("Count", "1", InputType.TYPE_CLASS_NUMBER)
        val amount = field("Amount", "0.00", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val total = TextView(this).apply {
            textSize = 19f
            setPadding(0, 10, 0, 14)
            text = "Total: ${formatMinor(0L)}"
        }
        val countAmountRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(count, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = 8 })
            addView(amount, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = 8 })
        }
        fun recalculate() {
            val c = count.text.toString().toLongOrNull()?.coerceAtLeast(1L) ?: 1L
            val unit = parseMinor(amount.text.toString())
            val calculated = try { Math.multiplyExact(c, unit) } catch (_: ArithmeticException) { Long.MAX_VALUE }
            total.text = if (calculated == Long.MAX_VALUE) "Total: —" else "Total: ${formatMinor(calculated)}"
        }
        count.addTextChangedListener(SimpleTextWatcher { recalculate() })
        amount.addTextChangedListener(SimpleTextWatcher { recalculate() })
        recalculate()

        val description = field("Description", "", InputType.TYPE_CLASS_TEXT)
        val category = dropdownField("Category", categories, "House Expenses")
        val wallet = dropdownField("Wallet", wallets, "Cash")
        val fromWallet = dropdownField("From Account / Wallet", wallets, "Cash")
        val toWallet = dropdownField("To Account / Wallet", wallets, "Bank")
        val itemsSection = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        val itemsSummary = TextView(this).apply { textSize = 14f; setPadding(0, 4, 0, 4) }
        val itemRows = mutableListOf<ItemDraft>()
        val addItemButton = TextView(this).apply {
            text = "+ Add item"
            textSize = 15f
            setPadding(0, 10, 0, 10)
            isClickable = true
        }
        addItemButton.setOnClickListener {
            val item = ItemDraft()
            itemRows.add(item)
            itemsSection.visibility = View.VISIBLE
            addItemEditor(itemsSection, item, itemsSummary)
            itemsSummary.text = "${itemRows.size} item(s)"
        }
        val photoButton = TextView(this).apply {
            text = "📷  Add product photo"
            textSize = 15f
            setPadding(0, 10, 0, 10)
            isClickable = true
        }
        photoButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE) }
            @Suppress("DEPRECATION") startActivityForResult(intent, PHOTO_REQUEST)
            photoButton.setText("📷  Product photo selected")
        }
        val memo = field("Memo (optional)", "", InputType.TYPE_CLASS_TEXT)

        fun selectType(type: String) {
            selectedType = type
            val active = listOf(incomeTab, expenseTab, transferTab)
            active.forEach { it.isSelected = it.text.toString() == type || (type == "Expense" && it.text.toString() == "Expenses") }
            val transfer = type == "Transfer"
            countAmountRow.visibility = if (transfer) View.GONE else View.VISIBLE
            total.visibility = if (transfer) View.GONE else View.VISIBLE
            category.visibility = if (transfer) View.GONE else View.VISIBLE
            wallet.visibility = if (transfer) View.GONE else View.VISIBLE
            fromWallet.visibility = if (transfer) View.VISIBLE else View.GONE
            toWallet.visibility = if (transfer) View.VISIBLE else View.GONE
            recurring.visibility = if (transfer) View.GONE else View.VISIBLE
            addItemButton.visibility = if (transfer) View.GONE else View.VISIBLE
            itemsSection.visibility = if (transfer) View.GONE else itemsSection.visibility
            itemsSummary.visibility = if (transfer) View.GONE else View.VISIBLE
            photoButton.visibility = if (transfer) View.GONE else View.VISIBLE
        }
        incomeTab.setOnClickListener { selectType("Income") }
        expenseTab.setOnClickListener { selectType("Expense") }
        transferTab.setOnClickListener { selectType("Transfer") }

        container.addView(tabs)
        container.addView(dateTime)
        container.addView(recurring)
        container.addView(countAmountRow)
        container.addView(total)
        container.addView(description)
        container.addView(addItemButton)
        container.addView(itemsSection)
        container.addView(itemsSummary)
        container.addView(category)
        container.addView(wallet)
        container.addView(fromWallet)
        container.addView(toWallet)
        container.addView(TextView(this).apply { text = "────────────"; setPadding(0, 6, 0, 2) })
        container.addView(photoButton)
        container.addView(memo)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Add Transaction")
            .setView(container)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()
        dialog.setOnShowListener {
            selectType("Expense")
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val desc = description.text.toString().trim()
                if (desc.isEmpty()) {
                    Snackbar.make(binding.root, "Enter a description", Snackbar.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (selectedType == "Transfer") {
                    val transferAmount = parseMinor(amount.text.toString())
                    val from = fromWallet.text.toString().trim().ifEmpty { "Cash" }
                    val to = toWallet.text.toString().trim().ifEmpty { "Bank" }
                    if (transferAmount <= 0L) {
                        Snackbar.make(binding.root, "Enter a valid transfer amount", Snackbar.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    if (from == to) {
                        Snackbar.make(binding.root, "From and To accounts must be different", Snackbar.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    recentTransactions.add(0, Transaction("Transfer", 1L, transferAmount, transferAmount, desc, "Transfer", "$from → $to", memo.text.toString().trim(), emptyList(), false))
                } else {
                    val c = count.text.toString().toLongOrNull()?.coerceAtLeast(1L) ?: 1L
                    val unit = parseMinor(amount.text.toString())
                    val calculatedTotal = try { Math.multiplyExact(c, unit) } catch (_: ArithmeticException) { Long.MAX_VALUE }
                    if (unit <= 0L || calculatedTotal == Long.MAX_VALUE) {
                        Snackbar.make(binding.root, "Enter a valid amount", Snackbar.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    val transactionType = if (selectedType == "Income") "Income" else "Expense"
                    val transaction = Transaction(transactionType, c, unit, calculatedTotal, desc, category.text.toString().trim().ifEmpty { "Uncategorized" }, wallet.text.toString().trim().ifEmpty { "Cash" }, memo.text.toString().trim(), itemRows.map { it.toTransactionItem() }, recurring.text.toString().endsWith("On"))
                    recentTransactions.add(0, transaction)
                    if (transactionType == "Income") {
                        incomeMinor += calculatedTotal
                        balanceMinor += calculatedTotal
                    } else {
                        expenseMinor += calculatedTotal
                        balanceMinor -= calculatedTotal
                    }
                }

                renderDashboard()
                renderTransactions()
                dialog.dismiss()
                showScreen(binding.transactionsScreen)
            }
        }
        dialog.show()
    }

    private fun entryTab(label: String): TextView = TextView(this).apply {
        text = label
        textSize = 14f
        gravity = android.view.Gravity.CENTER
        setPadding(8, 14, 8, 14)
        isClickable = true
        isFocusable = true
        alpha = if (label == "Expenses") 1f else .72f
    }

    private fun addItemEditor(parent: LinearLayout, item: ItemDraft, summary: TextView) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 8, 0, 8) }
        val name = field("Product name", "", InputType.TYPE_CLASS_TEXT)
        val qty = field("Quantity", "1", InputType.TYPE_CLASS_NUMBER)
        val price = field("Price", "0.00", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val itemTotal = TextView(this).apply { textSize = 13f; text = "Item total: ${formatMinor(0L)}" }
        fun update() {
            item.name = name.text.toString().trim()
            item.quantity = qty.text.toString().toLongOrNull()?.coerceAtLeast(1L) ?: 1L
            item.unitMinor = parseMinor(price.text.toString())
            val total = try { Math.multiplyExact(item.quantity, item.unitMinor) } catch (_: ArithmeticException) { 0L }
            itemTotal.text = "Item total: ${formatMinor(total)}"
            summary.text = "${parent.childCount - 1} item(s)"
        }
        name.addTextChangedListener(SimpleTextWatcher { update() })
        qty.addTextChangedListener(SimpleTextWatcher { update() })
        price.addTextChangedListener(SimpleTextWatcher { update() })
        row.addView(name); row.addView(qty); row.addView(price); row.addView(itemTotal); parent.addView(row)
    }

    private fun pickDateTime(target: TextView) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, day ->
            calendar.set(year, month, day)
            TimePickerDialog(this, { _, hour, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hour)
                calendar.set(Calendar.MINUTE, minute)
                target.text = formatDateTime(calendar.timeInMillis)
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
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
            row.addView(TextView(this).apply { text = "${transaction.type}  •  ${transaction.count} × ${formatMinor(transaction.unitMinor)} × ${formatMinor(transaction.totalMinor)}  •  ${transaction.category}"; textSize = 13f })
            row.addView(TextView(this).apply { text = transaction.wallet + if (transaction.recurring) "  •  Recurring" else ""; textSize = 12f })
            if (transaction.items.isNotEmpty()) row.addView(TextView(this).apply { text = "${transaction.items.size} item(s)"; textSize = 12f })
            binding.recentContainer.addView(row)
        }
    }

    private fun renderTransactions() {
        binding.transactionsContainer.removeAllViews()
        binding.transactionsEmpty.visibility = if (recentTransactions.isEmpty()) View.VISIBLE else View.GONE
        binding.transactionsSummary.text = "${recentTransactions.size} transaction(s)"
        recentTransactions.forEach { transaction ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 14, 16, 14) }
            row.addView(TextView(this).apply { text = transaction.description; textSize = 17f })
            row.addView(TextView(this).apply { text = "${transaction.type}  •  ${formatMinor(transaction.totalMinor)}"; textSize = 14f })
            row.addView(TextView(this).apply { text = if (transaction.type == "Transfer") transaction.wallet else "${transaction.category}  •  ${transaction.wallet}"; textSize = 12f })
            if (transaction.items.isNotEmpty()) row.addView(TextView(this).apply { text = "${transaction.items.size} item(s)"; textSize = 12f })
            binding.transactionsContainer.addView(row)
        }
    }

    private fun field(hint: String, value: String, inputType: Int) = EditText(this).apply {
        this.hint = hint
        setText(value)
        this.inputType = inputType
        textSize = 16f
        setSingleLine(true)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 }
    }

    private fun dropdownField(hint: String, values: List<String>, value: String): AutoCompleteTextView = AutoCompleteTextView(this).apply {
        this.hint = hint
        setText(value, false)
        setAdapter(ArrayAdapter(this@MainActivity, android.R.layout.simple_dropdown_item_1line, values))
        inputType = InputType.TYPE_CLASS_TEXT
        textSize = 16f
        setSingleLine(true)
        setOnClickListener { showDropDown() }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 }
    }

    private fun parseMinor(value: String): Long = try {
        BigDecimal(value.trim().ifEmpty { "0" }).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
    } catch (_: Exception) { 0L }

    private fun formatMinor(value: Long): String = NumberFormat.getCurrencyInstance(Locale.getDefault()).format(BigDecimal.valueOf(value, 2))
    private fun formatDateTime(millis: Long): String = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(millis))

    @Deprecated("Activity Result API migration will be handled with attachment persistence")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PHOTO_REQUEST && resultCode == RESULT_OK) data?.data?.let { uri ->
            photoUriHolder = uri
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    override fun onDestroy() { _binding = null; super.onDestroy() }

    private data class Transaction(val type: String, val count: Long, val unitMinor: Long, val totalMinor: Long, val description: String, val category: String, val wallet: String, val memo: String, val items: List<TransactionItem>, val recurring: Boolean)
    private data class TransactionItem(val name: String, val quantity: Long, val unitMinor: Long, val totalMinor: Long)
    private class ItemDraft {
        var name = ""
        var quantity = 1L
        var unitMinor = 0L
        fun toTransactionItem(): TransactionItem {
            val total = try { Math.multiplyExact(quantity, unitMinor) } catch (_: ArithmeticException) { 0L }
            return TransactionItem(name, quantity, unitMinor, total)
        }
    }
    private class SimpleTextWatcher(private val callback: () -> Unit) : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = callback()
        override fun afterTextChanged(s: android.text.Editable?) = Unit
    }
    private var photoUriHolder: Uri? = null
    companion object { private const val PHOTO_REQUEST = 7001 }
}
