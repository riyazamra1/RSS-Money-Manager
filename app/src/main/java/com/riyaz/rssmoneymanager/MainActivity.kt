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
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private var _binding: ActivityMainBinding? = null
    private val binding get() = checkNotNull(_binding)
    private var balanceMinor = 0L
    private var incomeMinor = 0L
    private var expenseMinor = 0L
    private val recentTransactions = mutableListOf<Transaction>()
    private val categories = mutableListOf<String>()
    private val defaultCategories = listOf("House Expenses", "Food", "Transport", "Bills", "Shopping", "Salary", "Other")
    private val wallets = mutableListOf<Wallet>()
    private val defaultWallets = listOf(
        Wallet("Cash", "Cash", 0L),
        Wallet("Bank", "Bank", 0L),
        Wallet("Card", "Card", 0L),
        Wallet("Savings", "Savings", 0L),
        Wallet("Other", "Other", 0L)
    )
    private val prefs by lazy { getSharedPreferences("money_manager", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupMagicNavigation()
        binding.menuButton.setOnClickListener { startActivity(Intent(this, RssKitMenuActivity::class.java)) }
        binding.addTransactionButton.setOnClickListener { showTransactionDialog() }
        binding.addWalletButton.setOnClickListener { showWalletDialog(null) }
        binding.homeAddButton.setOnClickListener { showTransactionDialog() }
        binding.homeAccountsButton.setOnClickListener { showScreen(binding.accountsScreen) }
        binding.homeCategoriesButton.setOnClickListener { showCategoryManager() }
        loadPersistedState()
        renderDashboard()
        renderTransactions()
        renderAccounts()
        when (intent.getStringExtra("open_screen")) {
            "transactions" -> showScreen(binding.transactionsScreen)
            "accounts" -> showScreen(binding.accountsScreen)
            "more" -> showScreen(binding.moreScreen)
        }
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
        val category = dropdownField("Category", categoryNames(), categoryNames().firstOrNull() ?: "Other")
        val walletNames = walletNames()
        val wallet = dropdownField("Wallet", walletNames, walletNames.firstOrNull() ?: "Cash")
        val fromWallet = dropdownField("From Account / Wallet", walletNames, walletNames.firstOrNull() ?: "Cash")
        val toWallet = dropdownField("To Account / Wallet", walletNames, walletNames.getOrNull(1) ?: walletNames.firstOrNull() ?: "Cash")
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

                persistState()
                renderDashboard()
                renderTransactions()
                renderAccounts()
                dialog.dismiss()
                showScreen(binding.transactionsScreen)
            }
        }
        dialog.show()
    }

    private fun categoryNames(): List<String> = categories.ifEmpty { defaultCategories }

    private fun showCategoryManager() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 4, 24, 0)
        }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val name = field("New category name", "", InputType.TYPE_CLASS_TEXT)
        container.addView(name)
        container.addView(list)

        fun refresh() {
            list.removeAllViews()
            categories.forEach { categoryName ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(0, 6, 0, 6)
                }
                row.addView(TextView(this).apply {
                    text = categoryName
                    textSize = 16f
                    setPadding(0, 8, 0, 8)
                }, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(TextView(this).apply {
                    text = "Edit"
                    textSize = 14f
                    isClickable = true
                    setPadding(12, 8, 12, 8)
                    setOnClickListener {
                        val edit = field("Category name", categoryName, InputType.TYPE_CLASS_TEXT)
                        MaterialAlertDialogBuilder(this@MainActivity)
                            .setTitle("Edit Category")
                            .setView(edit)
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Save") { _, _ ->
                                val newName = edit.text.toString().trim()
                                if (newName.isNotEmpty() && !categories.any { it.equals(newName, true) && it != categoryName }) {
                                    val index = categories.indexOf(categoryName)
                                    if (index >= 0) categories[index] = newName
                                    recentTransactions.forEach { if (it.category.equals(categoryName, true)) it.category = newName }
                                    persistState()
                                    refresh()
                                    renderDashboard()
                                    renderTransactions()
                                }
                            }.show()
                    }
                })
                row.addView(TextView(this).apply {
                    text = "Delete"
                    textSize = 14f
                    isClickable = true
                    setPadding(12, 8, 4, 8)
                    setOnClickListener {
                        MaterialAlertDialogBuilder(this@MainActivity)
                            .setTitle("Delete Category")
                            .setMessage("Delete " + categoryName + "? Existing transactions will keep their category.")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Delete") { _, _ ->
                                categories.remove(categoryName)
                                if (categories.isEmpty()) categories.add("Other")
                                persistState()
                                refresh()
                            }.show()
                    }
                })
                list.addView(row)
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Categories")
            .setMessage("Create and manage the categories used by new transactions.")
            .setView(container)
            .setNegativeButton("Close", null)
            .setPositiveButton("Add", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    refresh()
                    dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val newName = name.text.toString().trim()
                        if (newName.isEmpty()) {
                            Snackbar.make(binding.root, "Enter a category name", Snackbar.LENGTH_SHORT).show()
                        } else if (categories.any { it.equals(newName, true) }) {
                            Snackbar.make(binding.root, "Category already exists", Snackbar.LENGTH_SHORT).show()
                        } else {
                            categories.add(newName)
                            name.setText("")
                            persistState()
                            refresh()
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun walletNames(): List<String> = wallets.map { it.name }.ifEmpty { listOf("Cash") }

    private fun showWalletDialog(existing: Wallet?) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 0)
        }
        val name = field("Wallet name", existing?.name ?: "", InputType.TYPE_CLASS_TEXT)
        val type = dropdownField("Account type",
            listOf("Cash", "Bank", "Card", "Savings", "Credit", "Investment", "Other"),
            existing?.type ?: "Cash")
        val opening = field("Opening balance",
            existing?.openingMinor?.let { BigDecimal.valueOf(it, 2).toPlainString() } ?: "0.00",
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        container.addView(name)
        container.addView(type)
        container.addView(opening)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (existing == null) "Add Wallet" else "Edit Wallet")
            .setView(container)
            .setNegativeButton("Cancel", null)
            .setPositiveButton(if (existing == null) "Add" else "Save", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val newName = name.text.toString().trim()
                val newType = type.text.toString().trim().ifEmpty { "Other" }
                val newOpening = parseMinor(opening.text.toString())
                if (newName.isEmpty()) {
                    Snackbar.make(binding.root, "Enter a wallet name", Snackbar.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (wallets.any { it.name.equals(newName, true) && it !== existing }) {
                    Snackbar.make(binding.root, "A wallet with this name already exists", Snackbar.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (existing == null) {
                    wallets.add(Wallet(newName, newType, newOpening))
                    balanceMinor += newOpening
                } else {
                    val oldName = existing.name
                    balanceMinor += newOpening - existing.openingMinor
                    existing.name = newName
                    existing.type = newType
                    existing.openingMinor = newOpening
                    recentTransactions.forEach { transaction ->
                        if (transaction.type == "Transfer") {
                            transaction.wallet = transaction.wallet.replace(oldName + " → ", newName + " → ")
                                .replace(" → " + oldName, " → " + newName)
                        } else if (transaction.wallet.equals(oldName, true)) {
                            transaction.wallet = newName
                        }
                    }
                }
                persistState()
                renderDashboard()
                renderTransactions()
                renderAccounts()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun confirmDeleteWallet(wallet: Wallet) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Wallet")
            .setMessage("Delete " + wallet.name + "? Existing transaction history will be kept.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                val name = wallet.name
                balanceMinor -= wallet.openingMinor
                wallets.remove(wallet)
                recentTransactions.forEach { transaction ->
                    if (transaction.type == "Transfer") {
                        transaction.wallet = transaction.wallet.replace(name, "Deleted wallet")
                    } else if (transaction.wallet.equals(name, true)) {
                        transaction.wallet = "Deleted wallet"
                    }
                }
                if (wallets.isEmpty()) wallets.add(Wallet("Cash", "Cash", 0L))
                persistState()
                renderDashboard()
                renderTransactions()
                renderAccounts()
            }
            .show()
    }

    private fun renderAccounts() {
        binding.accountsContainer.removeAllViews()
        if (wallets.isEmpty()) wallets.addAll(defaultWallets.map { it.copy() })
        wallets.forEach { wallet ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(18, 16, 18, 16)
                setBackgroundResource(R.drawable.surface_card)
                isClickable = true
                setOnClickListener { showWalletDialog(wallet) }
                setOnLongClickListener { confirmDeleteWallet(wallet); true }
            }
            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            info.addView(TextView(this).apply {
                text = wallet.name
                textSize = 18f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            info.addView(TextView(this).apply {
                text = wallet.type
                textSize = 12f
            })
            header.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
            header.addView(TextView(this).apply {
                text = formatMinor(walletBalance(wallet))
                textSize = 17f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            card.addView(header)
            card.addView(TextView(this).apply {
                text = "Tap to edit • Long press to delete"
                textSize = 11f
                setPadding(0, 8, 0, 0)
            })
            binding.accountsContainer.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10 })
        }
    }

    private fun walletBalance(wallet: Wallet): Long {
        var balance = wallet.openingMinor
        recentTransactions.forEach { transaction ->
            when (transaction.type) {
                "Income" -> if (transaction.wallet.equals(wallet.name, true)) balance += transaction.totalMinor
                "Expense" -> if (transaction.wallet.equals(wallet.name, true)) balance -= transaction.totalMinor
                "Transfer" -> {
                    val parts = transaction.wallet.split(" → ")
                    if (parts.size == 2) {
                        if (parts[0].equals(wallet.name, true)) balance -= transaction.totalMinor
                        if (parts[1].equals(wallet.name, true)) balance += transaction.totalMinor
                    }
                }
            }
        }
        return balance
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

    private fun persistState() {
        val transactionsJson = JSONArray()
        recentTransactions.forEach { t ->
            transactionsJson.put(JSONObject().apply {
                put("type", t.type)
                put("count", t.count)
                put("unitMinor", t.unitMinor)
                put("totalMinor", t.totalMinor)
                put("description", t.description)
                put("category", t.category)
                put("wallet", t.wallet)
                put("memo", t.memo)
                put("recurring", t.recurring)
                val items = JSONArray()
                t.items.forEach { item ->
                    items.put(JSONObject().apply {
                        put("name", item.name)
                        put("quantity", item.quantity)
                        put("unitMinor", item.unitMinor)
                        put("totalMinor", item.totalMinor)
                    })
                }
                put("items", items)
            })
        }
        prefs.edit()
            .putLong("balanceMinor", balanceMinor)
            .putLong("incomeMinor", incomeMinor)
            .putLong("expenseMinor", expenseMinor)
            .putString("transactions", transactionsJson.toString())
            .putString("categories", JSONArray(categories).toString())
            .putString("wallets", JSONArray().apply {
                wallets.forEach { wallet ->
                    put(JSONObject().apply {
                        put("name", wallet.name)
                        put("type", wallet.type)
                        put("openingMinor", wallet.openingMinor)
                    })
                }
            }.toString())
            .apply()
    }

    private fun loadPersistedState() {
        balanceMinor = prefs.getLong("balanceMinor", 0L)
        incomeMinor = prefs.getLong("incomeMinor", 0L)
        expenseMinor = prefs.getLong("expenseMinor", 0L)
        categories.clear()
        val categoriesRaw = prefs.getString("categories", null)
        if (!categoriesRaw.isNullOrBlank()) {
            try {
                val savedCategories = JSONArray(categoriesRaw)
                for (i in 0 until savedCategories.length()) categories.add(savedCategories.optString(i))
            } catch (_: Exception) {
                categories.clear()
            }
        }
        if (categories.isEmpty()) categories.addAll(defaultCategories)
        wallets.clear()
        val walletsRaw = prefs.getString("wallets", null)
        if (!walletsRaw.isNullOrBlank()) {
            try {
                val savedWallets = JSONArray(walletsRaw)
                for (i in 0 until savedWallets.length()) {
                    val obj = savedWallets.getJSONObject(i)
                    wallets.add(Wallet(
                        obj.optString("name"),
                        obj.optString("type", "Other"),
                        obj.optLong("openingMinor", 0L)
                    ))
                }
            } catch (_: Exception) {
                wallets.clear()
            }
        }
        if (wallets.isEmpty()) wallets.addAll(defaultWallets.map { it.copy() })
        recentTransactions.clear()
        val raw = prefs.getString("transactions", null) ?: return
        try {
            val transactions = JSONArray(raw)
            for (i in 0 until transactions.length()) {
                val obj = transactions.getJSONObject(i)
                val itemsJson = obj.optJSONArray("items") ?: JSONArray()
                val items = buildList {
                    for (j in 0 until itemsJson.length()) {
                        val item = itemsJson.getJSONObject(j)
                        add(TransactionItem(
                            item.optString("name"),
                            item.optLong("quantity", 1L),
                            item.optLong("unitMinor", 0L),
                            item.optLong("totalMinor", 0L)
                        ))
                    }
                }
                recentTransactions.add(Transaction(
                    obj.optString("type"),
                    obj.optLong("count", 1L),
                    obj.optLong("unitMinor", 0L),
                    obj.optLong("totalMinor", 0L),
                    obj.optString("description"),
                    obj.optString("category"),
                    obj.optString("wallet"),
                    obj.optString("memo"),
                    items,
                    obj.optBoolean("recurring", false)
                ))
            }
        } catch (_: Exception) {
            recentTransactions.clear()
            balanceMinor = 0L
            incomeMinor = 0L
            expenseMinor = 0L
        }
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

    private data class Transaction(val type: String, val count: Long, val unitMinor: Long, val totalMinor: Long, val description: String, var category: String, var wallet: String, val memo: String, val items: List<TransactionItem>, val recurring: Boolean)
    private data class Wallet(var name: String, var type: String, var openingMinor: Long)
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
