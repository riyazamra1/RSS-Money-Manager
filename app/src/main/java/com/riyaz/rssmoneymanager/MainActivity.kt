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
    private val budgets = mutableListOf<Budget>()
    private val savingsGoals = mutableListOf<SavingsGoal>()
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
        RssDynamicPricing.refresh(this)
        setupMagicNavigation()
        binding.menuButton.setOnClickListener { startActivity(Intent(this, RssKitMenuActivity::class.java)) }
        binding.addTransactionButton.setOnClickListener { showTransactionDialog() }
        binding.addWalletButton.setOnClickListener { showWalletDialog(null) }
        binding.homeAddButton.setOnClickListener { showTransactionDialog() }
        binding.homeAccountsButton.setOnClickListener { showScreen(binding.accountsScreen) }
        binding.homeCategoriesButton.setOnClickListener { showCategoryManager() }
        loadPersistedState()
        binding.budgetsButton.setOnClickListener { showBudgetManager() }
        binding.savingsGoalsButton.setOnClickListener { showSavingsGoalManager() }
        binding.reportsButton.setOnClickListener { showReports() }
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

    private fun showTransactionDialog() = showTransactionEditor()

    private fun showTransactionEditor(existing: Transaction? = null, editIndex: Int = -1) {
        val root = binding.root as? android.view.ViewGroup ?: return
        val page = android.widget.FrameLayout(this).apply { setBackgroundColor(resolveThemeColor(android.R.attr.colorBackground)); elevation = 24f }
        val scroll = android.widget.ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 8, 20, 24) }
        val toolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; minimumHeight = 64 }
        val back = TextView(this).apply { text = "‹"; textSize = 38f; gravity = android.view.Gravity.CENTER; setPadding(4, 0, 18, 0); isClickable = true; contentDescription = "Back" }
        val title = TextView(this).apply { text = if (existing == null) "Add Transaction" else "Edit Transaction"; textSize = 21f; setTypeface(typeface, android.graphics.Typeface.BOLD); layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        val deleteButton = TextView(this).apply {
            text = if (existing != null) "DELETE" else ""
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(10, 12, 12, 12)
            isClickable = existing != null
            visibility = if (existing != null) View.VISIBLE else View.GONE
            contentDescription = "Delete transaction"
        }
        val save = TextView(this).apply { text = "SAVE"; textSize = 14f; setTypeface(typeface, android.graphics.Typeface.BOLD); setPadding(12, 12, 4, 12); isClickable = true }
        toolbar.addView(back); toolbar.addView(title); toolbar.addView(deleteButton); toolbar.addView(save); content.addView(toolbar)
        var selectedType = existing?.type ?: "Expense"
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 4, 0, 16) }
        val incomeTab = entryTab("Income"); val expenseTab = entryTab("Expenses"); val transferTab = entryTab("Transfer")
        tabs.addView(incomeTab, LinearLayout.LayoutParams(0, -2, 1f)); tabs.addView(expenseTab, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = 6; marginEnd = 6 }); tabs.addView(transferTab, LinearLayout.LayoutParams(0, -2, 1f))
        val dateTime = TextView(this).apply { text = formatDateTime(existing?.timestampMillis ?: System.currentTimeMillis()); textSize = 15f; setPadding(0, 12, 0, 12); isClickable = true }
        dateTime.setOnClickListener { pickDateTime(dateTime) }
        val recurring = TextView(this).apply { text = if (existing?.recurring == true) "↻  Recurring: On" else "↻  Recurring: Off"; textSize = 15f; setPadding(0, 8, 0, 14); isClickable = true }
        recurring.setOnClickListener { recurring.text = if (recurring.text.toString().endsWith("Off")) "↻  Recurring: On" else "↻  Recurring: Off" }
        val count = field("Count", (existing?.count ?: 1L).toString(), InputType.TYPE_CLASS_NUMBER)
        val amount = field("Amount", BigDecimal.valueOf(existing?.unitMinor ?: 0L, 2).toPlainString(), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val total = TextView(this).apply { textSize = 19f; setPadding(0, 10, 0, 16); setTypeface(typeface, android.graphics.Typeface.BOLD) }
        val countAmountRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; addView(count, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = 8 }); addView(amount, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = 8 }) }
        fun recalculate() { val c = count.text.toString().toLongOrNull()?.coerceAtLeast(1L) ?: 1L; val unit = parseMinor(amount.text.toString()); val calculated = try { Math.multiplyExact(c, unit) } catch (_: ArithmeticException) { Long.MAX_VALUE }; total.text = if (calculated == Long.MAX_VALUE) "Total: —" else "Total: ${formatMinor(calculated)}" }
        count.addTextChangedListener(SimpleTextWatcher { recalculate() }); amount.addTextChangedListener(SimpleTextWatcher { recalculate() })
        val description = field("Description", existing?.description ?: "", InputType.TYPE_CLASS_TEXT)
        val categoryValues = categoryNames(); val category = dropdownField("Category", categoryValues, existing?.category ?: categoryValues.firstOrNull().orEmpty())
        val walletValues = walletNames(); val wallet = dropdownField("Wallet", walletValues, existing?.wallet ?: walletValues.firstOrNull().orEmpty())
        val fromWallet = dropdownField("From Account / Wallet", walletValues, walletValues.firstOrNull().orEmpty()); val toWallet = dropdownField("To Account / Wallet", walletValues, walletValues.getOrNull(1) ?: walletValues.firstOrNull().orEmpty())
        val itemsSection = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val itemRows = existing?.items?.map { item -> ItemDraft().apply { name = item.name; quantity = item.quantity; unitMinor = item.unitMinor } }?.toMutableList() ?: mutableListOf()
        val itemsSummary = TextView(this).apply { textSize = 14f; setPadding(0, 4, 0, 6) }
        val addItemButton = TextView(this).apply { text = "+ Add item"; textSize = 15f; setPadding(0, 10, 0, 10); isClickable = true }
        fun renderItems() {
            itemsSection.removeAllViews(); itemsSummary.text = if (itemRows.isEmpty()) "" else "${itemRows.size} item(s)"
            itemRows.forEachIndexed { index, item ->
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, 6, 0, 6) }
                row.addView(TextView(this).apply { text = "${item.name.ifBlank { "Item" }}  •  ${item.quantity} × ${formatMinor(item.unitMinor)}"; textSize = 14f; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
                row.addView(TextView(this).apply { text = "Remove"; isClickable = true; setPadding(10, 8, 0, 8); setOnClickListener { itemRows.removeAt(index); renderItems() } })
                itemsSection.addView(row)
            }
            itemsSection.visibility = if (itemRows.isEmpty()) View.GONE else View.VISIBLE
        }
        fun addItemEditor() {
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 8, 0, 8) }
            val name = field("Product name", "", InputType.TYPE_CLASS_TEXT); val qty = field("Quantity", "1", InputType.TYPE_CLASS_NUMBER); val price = field("Unit price", "0.00", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
            box.addView(name); box.addView(qty); box.addView(price)
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.END }
            val cancel = TextView(this).apply { text = "Cancel"; isClickable = true; setPadding(14, 10, 14, 10) }; val add = TextView(this).apply { text = "Add"; isClickable = true; setPadding(14, 10, 4, 10) }
            actions.addView(cancel); actions.addView(add); box.addView(actions); cancel.setOnClickListener { content.removeView(box) }
            add.setOnClickListener { val q = qty.text.toString().toLongOrNull()?.coerceAtLeast(1L) ?: 1L; val u = parseMinor(price.text.toString()); if (name.text.toString().trim().isEmpty() || u <= 0L) { Snackbar.make(binding.root, "Enter a product name and valid price", Snackbar.LENGTH_SHORT).show(); return@setOnClickListener }; itemRows.add(ItemDraft().apply { this.name = name.text.toString().trim(); quantity = q; unitMinor = u }); content.removeView(box); renderItems() }
            content.addView(box, content.indexOfChild(itemsSection))
        }
        addItemButton.setOnClickListener { addItemEditor() }; renderItems()
        val photoButton = TextView(this).apply { text = "📷  Add product photo"; textSize = 15f; setPadding(0, 10, 0, 10); isClickable = true }
        photoButton.setOnClickListener { val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE) }; @Suppress("DEPRECATION") startActivityForResult(intent, PHOTO_REQUEST); photoButton.text = "📷  Product photo selected" }
        val memo = field("Memo (optional)", existing?.memo ?: "", InputType.TYPE_CLASS_TEXT)
        fun selectType(type: String) {
            selectedType = type; listOf(incomeTab, expenseTab, transferTab).forEach { it.isSelected = it.text.toString() == type || (type == "Expense" && it.text.toString() == "Expenses") }
            val transfer = type == "Transfer"; countAmountRow.visibility = if (transfer) View.GONE else View.VISIBLE; total.visibility = if (transfer) View.GONE else View.VISIBLE; category.visibility = if (transfer) View.GONE else View.VISIBLE; wallet.visibility = if (transfer) View.GONE else View.VISIBLE; fromWallet.visibility = if (transfer) View.VISIBLE else View.GONE; toWallet.visibility = if (transfer) View.VISIBLE else View.GONE; recurring.visibility = if (transfer) View.GONE else View.VISIBLE; addItemButton.visibility = if (transfer) View.GONE else View.VISIBLE; itemsSection.visibility = if (transfer || itemRows.isEmpty()) View.GONE else View.VISIBLE; itemsSummary.visibility = if (transfer) View.GONE else View.VISIBLE; photoButton.visibility = if (transfer) View.GONE else View.VISIBLE
        }
        incomeTab.setOnClickListener { selectType("Income") }; expenseTab.setOnClickListener { selectType("Expense") }; transferTab.setOnClickListener { selectType("Transfer") }
        content.addView(tabs); content.addView(dateTime); content.addView(recurring); content.addView(countAmountRow); content.addView(total); content.addView(description); content.addView(addItemButton); content.addView(itemsSection); content.addView(itemsSummary); content.addView(category); content.addView(wallet); content.addView(fromWallet); content.addView(toWallet); content.addView(TextView(this).apply { text = "────────────────"; setPadding(0, 8, 0, 2) }); content.addView(memo); content.addView(photoButton)
        scroll.addView(content); page.addView(scroll, android.widget.FrameLayout.LayoutParams(-1, -1)); root.addView(page, android.view.ViewGroup.LayoutParams(-1, -1))
        fun closePage() { root.removeView(page) }; back.setOnClickListener { closePage() }
        deleteButton.setOnClickListener {
            if (editIndex >= 0 && editIndex < recentTransactions.size) {
                recentTransactions.removeAt(editIndex)
                recalculateFinancialState()
                persistState()
                renderDashboard()
                renderTransactions()
                renderAccounts()
                closePage()
                showScreen(binding.transactionsScreen)
                Snackbar.make(binding.root, "Transaction deleted", Snackbar.LENGTH_SHORT).show()
            }
        }
        save.setOnClickListener {
            val desc = description.text.toString().trim(); if (desc.isEmpty()) { Snackbar.make(binding.root, "Enter a description", Snackbar.LENGTH_SHORT).show(); return@setOnClickListener }
            val timestamp = parseReportDate(dateTime.text.toString()); val transaction: Transaction
            if (selectedType == "Transfer") {
                val transferAmount = parseMinor(amount.text.toString()); val from = fromWallet.text.toString().trim().ifEmpty { "Cash" }; val to = toWallet.text.toString().trim().ifEmpty { "Bank" }
                if (transferAmount <= 0L || from == to) { Snackbar.make(binding.root, "Enter a valid transfer and use different accounts", Snackbar.LENGTH_SHORT).show(); return@setOnClickListener }
                transaction = Transaction("Transfer", 1L, transferAmount, transferAmount, desc, "Transfer", "$from → $to", memo.text.toString().trim(), emptyList(), false, timestamp)
            } else {
                val c = count.text.toString().toLongOrNull()?.coerceAtLeast(1L) ?: 1L; val unit = parseMinor(amount.text.toString()); val calculated = try { Math.multiplyExact(c, unit) } catch (_: ArithmeticException) { Long.MAX_VALUE }
                if (unit <= 0L || calculated == Long.MAX_VALUE) { Snackbar.make(binding.root, "Enter a valid amount", Snackbar.LENGTH_SHORT).show(); return@setOnClickListener }
                transaction = Transaction(if (selectedType == "Income") "Income" else "Expense", c, unit, calculated, desc, category.text.toString().trim().ifEmpty { "Uncategorized" }, wallet.text.toString().trim().ifEmpty { "Cash" }, memo.text.toString().trim(), itemRows.map { it.toTransactionItem() }, recurring.text.toString().endsWith("On"), timestamp)
            }
            if (editIndex >= 0 && editIndex < recentTransactions.size) recentTransactions[editIndex] = transaction else recentTransactions.add(0, transaction)
            recalculateFinancialState(); persistState(); renderDashboard(); renderTransactions(); renderAccounts(); closePage(); showScreen(binding.transactionsScreen)
        }
        selectType(selectedType); recalculate()
    }

    private fun resolveThemeColor(attr: Int): Int {
        val tv = android.util.TypedValue(); theme.resolveAttribute(attr, tv, true)
        return if (tv.resourceId != 0) androidx.core.content.ContextCompat.getColor(this, tv.resourceId) else tv.data
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
                } else {
                    val oldName = existing.name
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
                recalculateFinancialState()
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
                wallets.remove(wallet)
                recentTransactions.forEach { transaction ->
                    if (transaction.type == "Transfer") {
                        transaction.wallet = transaction.wallet.replace(name, "Deleted wallet")
                    } else if (transaction.wallet.equals(name, true)) {
                        transaction.wallet = "Deleted wallet"
                    }
                }
                if (wallets.isEmpty()) wallets.add(Wallet("Cash", "Cash", 0L))
                recalculateFinancialState()
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
    private fun recalculateFinancialState() {
        balanceMinor = wallets.sumOf { wallet -> walletBalance(wallet) }
        incomeMinor = recentTransactions.filter { it.type == "Income" }.sumOf { it.totalMinor }
        expenseMinor = recentTransactions.filter { it.type == "Expense" }.sumOf { it.totalMinor }
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
                put("timestampMillis", t.timestampMillis)
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
            .putString("budgets", JSONArray().apply {
                budgets.forEach { b -> put(JSONObject().apply { put("name", b.name); put("category", b.category); put("limitMinor", b.limitMinor) }) }
            }.toString())
            .putString("savingsGoals", JSONArray().apply {
                savingsGoals.forEach { g -> put(JSONObject().apply { put("name", g.name); put("targetMinor", g.targetMinor); put("currentMinor", g.currentMinor); put("targetDate", g.targetDate) }) }
            }.toString())
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
        budgets.clear()
        prefs.getString("budgets", null)?.let { raw -> try { val a=JSONArray(raw); for(i in 0 until a.length()) a.getJSONObject(i).let { budgets.add(Budget(it.optString("name"),it.optString("category"),it.optLong("limitMinor"))) } } catch (_: Exception) {} }
        savingsGoals.clear()
        prefs.getString("savingsGoals", null)?.let { raw -> try { val a=JSONArray(raw); for(i in 0 until a.length()) a.getJSONObject(i).let { savingsGoals.add(SavingsGoal(it.optString("name"),it.optLong("targetMinor"),it.optLong("currentMinor"),it.optString("targetDate"))) } } catch (_: Exception) {} }
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
                    obj.optBoolean("recurring", false),
                    obj.optLong("timestampMillis", System.currentTimeMillis())
                ))
            }
        } catch (_: Exception) {
            recentTransactions.clear()
        }
        recalculateFinancialState()
    }


    private fun showBudgetManager() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 4, 24, 0) }
        val name = field("Budget name", "", InputType.TYPE_CLASS_TEXT)
        val category = dropdownField("Category", listOf("Overall") + categoryNames(), "Overall")
        val limit = field("Limit", "0.00", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(name); box.addView(category); box.addView(limit); box.addView(list)
        fun spentFor(cat: String): Long = recentTransactions.filter { it.type == "Expense" && (cat == "Overall" || it.category.equals(cat, true)) }.sumOf { it.totalMinor }
        lateinit var refresh: () -> Unit
        fun renderBudget(budget: Budget, index: Int) {
            val spent = spentFor(budget.category)
            val remaining = budget.limitMinor - spent
            val percent = if (budget.limitMinor > 0L) ((spent.toDouble() / budget.limitMinor.toDouble()) * 100.0).coerceIn(0.0, 100.0).toInt() else 0
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 12, 0, 12) }
            row.addView(TextView(this).apply { text = budget.name + " • " + budget.category; textSize = 16f })
            row.addView(TextView(this).apply { text = "Spent " + formatMinor(spent) + " / " + formatMinor(budget.limitMinor) + " • " + percent + "%"; textSize = 13f })
            row.addView(android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = percent; layoutParams = LinearLayout.LayoutParams(-1, 12).apply { topMargin = 6; bottomMargin = 6 } })
            row.addView(TextView(this).apply { text = if (remaining >= 0L) formatMinor(remaining) + " remaining" else formatMinor(-remaining) + " over budget"; textSize = 13f })
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            actions.addView(TextView(this).apply {
                text = "Edit"; isClickable = true; setPadding(12, 8, 16, 8)
                setOnClickListener {
                    val editBox = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 4, 24, 0) }
                    val editName = field("Budget name", budget.name, InputType.TYPE_CLASS_TEXT)
                    val editCategory = dropdownField("Category", listOf("Overall") + categoryNames(), budget.category)
                    val editLimit = field("Limit", BigDecimal.valueOf(budget.limitMinor, 2).toPlainString(), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
                    editBox.addView(editName); editBox.addView(editCategory); editBox.addView(editLimit)
                    MaterialAlertDialogBuilder(this@MainActivity).setTitle("Edit Budget").setView(editBox).setNegativeButton("Cancel", null).setPositiveButton("Save") { _, _ ->
                        val newName = editName.text.toString().trim(); val newLimit = parseMinor(editLimit.text.toString())
                        if (newName.isNotEmpty() && newLimit > 0L) { budgets[index] = Budget(newName, editCategory.text.toString().ifBlank { "Overall" }, newLimit); persistState(); refresh() }
                    }.show()
                }
            })
            actions.addView(TextView(this).apply {
                text = "Delete"; isClickable = true; setPadding(16, 8, 12, 8)
                setOnClickListener { MaterialAlertDialogBuilder(this@MainActivity).setTitle("Delete Budget").setMessage("Delete " + budget.name + "?").setNegativeButton("Cancel", null).setPositiveButton("Delete") { _, _ -> budgets.removeAt(index); persistState(); refresh() }.show() }
            })
            row.addView(actions); list.addView(row)
        }
        refresh = {
            list.removeAllViews()
            if (budgets.isEmpty()) list.addView(TextView(this).apply { text = "No budgets yet. Add a budget to track spending progress."; textSize = 14f; setPadding(0, 12, 0, 12) })
            else budgets.forEachIndexed { index, budget -> renderBudget(budget, index) }
        }
        MaterialAlertDialogBuilder(this).setTitle("Budgets").setView(box).setNegativeButton("Close", null).setPositiveButton("Add Budget", null).create().also { dialog ->
            dialog.setOnShowListener {
                refresh()
                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val newName = name.text.toString().trim(); val newLimit = parseMinor(limit.text.toString()); val newCategory = category.text.toString().ifBlank { "Overall" }
                    if (newName.isEmpty() || newLimit <= 0L) Snackbar.make(binding.root, "Enter a budget name and valid limit", Snackbar.LENGTH_SHORT).show()
                    else { budgets.add(Budget(newName, newCategory, newLimit)); name.setText(""); limit.setText("0.00"); persistState(); refresh() }
                }
            }; dialog.show()
        }
    }

    private fun showReports() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 4, 24, 0)
        }
        val period = dropdownField("Period", listOf("All time", "This month", "This year"), "All time")
        val summary = TextView(this).apply { textSize = 15f; setPadding(0, 12, 0, 8) }
        val categoryList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val walletList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(period); box.addView(summary)
        box.addView(TextView(this).apply { text = "Expense by category"; textSize = 17f; setPadding(0, 12, 0, 6) })
        box.addView(categoryList)
        box.addView(TextView(this).apply { text = "Expense by wallet"; textSize = 17f; setPadding(0, 16, 0, 6) })
        box.addView(walletList)
        fun inPeriod(t: Transaction): Boolean {
            val now = Calendar.getInstance()
            val date = Calendar.getInstance().apply { timeInMillis = t.timestampMillis }
            return when (period.text.toString()) {
                "This month" -> now.get(Calendar.YEAR) == date.get(Calendar.YEAR) && now.get(Calendar.MONTH) == date.get(Calendar.MONTH)
                "This year" -> now.get(Calendar.YEAR) == date.get(Calendar.YEAR)
                else -> true
            }
        }
        fun refresh() {
            val filtered = recentTransactions.filter(::inPeriod)
            val income = filtered.filter { it.type == "Income" }.sumOf { it.totalMinor }
            val expense = filtered.filter { it.type == "Expense" }.sumOf { it.totalMinor }
            val transfers = filtered.filter { it.type == "Transfer" }.sumOf { it.totalMinor }
            summary.text = "Income: ${formatMinor(income)}\nExpenses: ${formatMinor(expense)}\nNet: ${formatMinor(income - expense)}\nTransfers: ${formatMinor(transfers)}\nTransactions: ${filtered.size}"
            categoryList.removeAllViews()
            filtered.filter { it.type == "Expense" }.groupBy { it.category }.entries.sortedByDescending { it.value.sumOf(Transaction::totalMinor) }.forEach { (category, items) ->
                categoryList.addView(TextView(this).apply { text = "$category • ${formatMinor(items.sumOf(Transaction::totalMinor))}"; textSize = 14f; setPadding(0, 5, 0, 5) })
            }
            if (categoryList.childCount == 0) categoryList.addView(TextView(this).apply { text = "No expense data"; textSize = 13f })
            walletList.removeAllViews()
            filtered.filter { it.type == "Expense" }.groupBy { it.wallet }.entries.sortedByDescending { it.value.sumOf(Transaction::totalMinor) }.forEach { (wallet, items) ->
                walletList.addView(TextView(this).apply { text = "$wallet • ${formatMinor(items.sumOf(Transaction::totalMinor))}"; textSize = 14f; setPadding(0, 5, 0, 5) })
            }
            if (walletList.childCount == 0) walletList.addView(TextView(this).apply { text = "No expense data"; textSize = 13f })
        }
        period.setOnItemClickListener { _, _, _, _ -> refresh() }
        refresh()
        MaterialAlertDialogBuilder(this).setTitle("Reports & Statistics").setView(box).setNegativeButton("Close", null).show()
    }

    private fun parseReportDate(value: String): Long = try {
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).parse(value)?.time ?: System.currentTimeMillis()
    } catch (_: Exception) { System.currentTimeMillis() }
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
            row.isClickable = true
            row.setOnClickListener { val index = recentTransactions.indexOf(transaction); if (index >= 0) showTransactionEditor(transaction, index) }
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
            row.isClickable = true
            row.setOnClickListener { val index = recentTransactions.indexOf(transaction); if (index >= 0) showTransactionEditor(transaction, index) }
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

    private data class Transaction(val type: String, val count: Long, val unitMinor: Long, val totalMinor: Long, val description: String, var category: String, var wallet: String, val memo: String, val items: List<TransactionItem>, val recurring: Boolean, val timestampMillis: Long = System.currentTimeMillis())
    private data class Wallet(var name: String, var type: String, var openingMinor: Long)
    private data class Budget(val name:String,val category:String,val limitMinor:Long)
    private data class SavingsGoal(val name:String,val targetMinor:Long,val currentMinor:Long,val targetDate:String)
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

    private fun showSavingsGoalManager() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 4, 24, 0) }
        val name = field("Goal name", "", InputType.TYPE_CLASS_TEXT)
        val target = field("Target amount", "0.00", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val current = field("Current saved", "0.00", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val date = field("Target date (optional)", "", InputType.TYPE_CLASS_TEXT)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(name); box.addView(target); box.addView(current); box.addView(date); box.addView(list)
        lateinit var refresh: () -> Unit
        fun renderGoal(goal: SavingsGoal, index: Int) {
            val percent = if (goal.targetMinor > 0L) ((goal.currentMinor.toDouble() / goal.targetMinor.toDouble()) * 100.0).coerceIn(0.0, 100.0).toInt() else 0
            val remaining = (goal.targetMinor - goal.currentMinor).coerceAtLeast(0L)
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 12, 0, 12) }
            row.addView(TextView(this).apply { text = goal.name + " • $percent%"; textSize = 16f })
            row.addView(TextView(this).apply { text = formatMinor(goal.currentMinor) + " / " + formatMinor(goal.targetMinor) + if (goal.targetDate.isNotBlank()) " • " + goal.targetDate else ""; textSize = 13f })
            row.addView(android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = percent; layoutParams = LinearLayout.LayoutParams(-1, 12).apply { topMargin = 6; bottomMargin = 6 } })
            row.addView(TextView(this).apply { text = if (remaining > 0L) formatMinor(remaining) + " remaining" else "Goal reached"; textSize = 13f })
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            actions.addView(TextView(this).apply {
                text = "Edit"; isClickable = true; setPadding(12, 8, 16, 8)
                setOnClickListener {
                    val editBox = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 4, 24, 0) }
                    val editName = field("Goal name", goal.name, InputType.TYPE_CLASS_TEXT)
                    val editTarget = field("Target amount", BigDecimal.valueOf(goal.targetMinor, 2).toPlainString(), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
                    val editCurrent = field("Current saved", BigDecimal.valueOf(goal.currentMinor, 2).toPlainString(), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
                    val editDate = field("Target date (optional)", goal.targetDate, InputType.TYPE_CLASS_TEXT)
                    editBox.addView(editName); editBox.addView(editTarget); editBox.addView(editCurrent); editBox.addView(editDate)
                    MaterialAlertDialogBuilder(this@MainActivity).setTitle("Edit Savings Goal").setView(editBox).setNegativeButton("Cancel", null).setPositiveButton("Save") { _, _ ->
                        val newName = editName.text.toString().trim(); val newTarget = parseMinor(editTarget.text.toString()); val newCurrent = parseMinor(editCurrent.text.toString()).coerceAtLeast(0L)
                        if (newName.isNotEmpty() && newTarget > 0L) { savingsGoals[index] = SavingsGoal(newName, newTarget, newCurrent.coerceAtMost(newTarget), editDate.text.toString().trim()); persistState(); refresh() }
                    }.show()
                }
            })
            actions.addView(TextView(this).apply {
                text = "Delete"; isClickable = true; setPadding(16, 8, 12, 8)
                setOnClickListener { MaterialAlertDialogBuilder(this@MainActivity).setTitle("Delete Savings Goal").setMessage("Delete " + goal.name + "?").setNegativeButton("Cancel", null).setPositiveButton("Delete") { _, _ -> savingsGoals.removeAt(index); persistState(); refresh() }.show() }
            })
            row.addView(actions); list.addView(row)
        }
        refresh = {
            list.removeAllViews()
            if (savingsGoals.isEmpty()) list.addView(TextView(this).apply { text = "No savings goals yet. Add a goal to track progress."; textSize = 14f; setPadding(0, 12, 0, 12) })
            else savingsGoals.forEachIndexed { index, goal -> renderGoal(goal, index) }
        }
        MaterialAlertDialogBuilder(this).setTitle("Savings Goals").setView(box).setNegativeButton("Close", null).setPositiveButton("Add Goal", null).create().also { dialog ->
            dialog.setOnShowListener {
                refresh()
                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val newName = name.text.toString().trim(); val newTarget = parseMinor(target.text.toString()); val newCurrent = parseMinor(current.text.toString()).coerceAtLeast(0L)
                    if (newName.isEmpty() || newTarget <= 0L) Snackbar.make(binding.root, "Enter a goal name and valid target", Snackbar.LENGTH_SHORT).show()
                    else { savingsGoals.add(SavingsGoal(newName, newTarget, newCurrent.coerceAtMost(newTarget), date.text.toString().trim())); name.setText(""); target.setText("0.00"); current.setText("0.00"); date.setText(""); persistState(); refresh() }
                }
            }; dialog.show()
        }
    }

}