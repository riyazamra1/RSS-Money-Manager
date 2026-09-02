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

        val photoUri = arrayOfNulls<Uri>(1)
        val photoButton = TextView(this).apply {
            text = "📷  Add product photo"
            textSize = 15f
            setPadding(0, 10, 0, 10)
            isClickable = true
        }
        photoButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            @Suppress("DEPRECATION")
            startActivityForResult(intent, PHOTO_REQUEST)
            photoButton.setText("📷  Product photo selected")
        }

        val memo = field("Memo (optional)", "", InputType.TYPE_CLASS_TEXT)

        container.addView(type)
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
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val c = count.text.toString().toLongOrNull()?.coerceAtLeast(1L) ?: 1L
                val unit = parseMinor(amount.text.toString())
                val calculatedTotal = try { Math.multiplyExact(c, unit) } catch (_: ArithmeticException) { Long.MAX_VALUE }
                val desc = description.text.toString().trim()
                if (unit <= 0L || desc.isEmpty() || calculatedTotal == Long.MAX_VALUE) {
                    Snackbar.make(binding.root, "Enter a valid amount and description", Snackbar.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val transaction = Transaction(
                    type.text.toString(), c, unit, calculatedTotal, desc,
                    category.text.toString().trim().ifEmpty { "Uncategorized" },
                    wallet.text.toString().trim().ifEmpty { "Cash" },
                    memo.text.toString().trim(), itemRows.map { it.toTransactionItem() },
                    recurring.text.toString().endsWith("On")
                )
                recentTransactions.add(0, transaction)
                if (transaction.type == "Income") {
                    incomeMinor += calculatedTotal
                    balanceMinor += calculatedTotal
                } else {
                    expenseMinor += calculatedTotal
                    balanceMinor -= calculatedTotal
                }
                renderDashboard()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun addItemEditor(parent: LinearLayout, item: ItemDraft, summary: TextView) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 8)
        }
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
        row.addView(name)
        row.addView(qty)
        row.addView(price)
        row.addView(itemTotal)
        parent.addView(row)
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
            row.addView(TextView(this).apply {
                text = "${transaction.count} × ${formatMinor(transaction.unitMinor)} × ${formatMinor(transaction.totalMinor)}  •  ${transaction.category}"
                textSize = 13f
            })
            row.addView(TextView(this).apply {
                text = transaction.wallet + if (transaction.recurring) "  •  Recurring" else ""
                textSize = 12f
            })
            if (transaction.items.isNotEmpty()) {
                row.addView(TextView(this).apply { text = "${transaction.items.size} item(s)"; textSize = 12f })
            }
            binding.recentContainer.addView(row)
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

    private fun dropdownField(hint: String, values: List<String>, value: String): AutoCompleteTextView =
        AutoCompleteTextView(this).apply {
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
        BigDecimal(value.trim().ifEmpty { "0" })
            .setScale(2, RoundingMode.HALF_UP)
            .movePointRight(2)
            .longValueExact()
    } catch (_: Exception) { 0L }

    private fun formatMinor(value: Long): String =
        NumberFormat.getCurrencyInstance(Locale.getDefault()).format(BigDecimal.valueOf(value, 2))

    private fun formatDateTime(millis: Long): String =
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(millis))

    @Deprecated("Activity Result API migration will be handled with attachment persistence")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PHOTO_REQUEST && resultCode == RESULT_OK) data?.data?.let { uri ->
            photoUriHolder = uri
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    override fun onDestroy() {
        _binding = null
        super.onDestroy()
    }

    private data class Transaction(
        val type: String,
        val count: Long,
        val unitMinor: Long,
        val totalMinor: Long,
        val description: String,
        val category: String,
        val wallet: String,
        val memo: String,
        val items: List<TransactionItem>,
        val recurring: Boolean
    )

    private data class TransactionItem(
        val name: String,
        val quantity: Long,
        val unitMinor: Long,
        val totalMinor: Long
    )

    private class ItemDraft {
        var name: String = ""
        var quantity: Long = 1L
        var unitMinor: Long = 0L
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

    companion object {
        private const val PHOTO_REQUEST = 7001
    }
}
