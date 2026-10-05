package com.riyaz.rssmoneymanager

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.Executors
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar

class OnboardingActivity : AppCompatActivity() {
    private lateinit var root: LinearLayout
    private val accountClient by lazy { RssKitAccountClient(this) }
    private val accountExecutor = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("money_manager", MODE_PRIVATE) }
    private var page = 0

    private val pages = listOf(
        "Welcome to RSS Money Manager" to "Track income, expenses, wallets and financial goals in one clean workspace.",
        "See your money clearly" to "Dashboard summaries, transactions and reports are designed to stay simple and readable.",
        "Stay in control" to "Use budgets, recurring transactions, backup, cloud sync and premium features as they become available."
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)) {
            openMain()
            return
        }
        showRegistration()
    }

    private fun showRegistration() {
        page = 0
        root = baseRoot()
        title("Create your RSS Money Manager account")
        subtitle("Your account is handled through RSS KIT. RSS KIT connects this app to the central account service; this screen never creates a local-only account or fakes verification.")

        val name = edit("Full name", "Enter your full name")
        val email = edit("Email", "Email address")
        val terms = CheckBox(this).apply {
            text = "I agree to the Terms & Conditions and Privacy Policy."
            setTextColor(getColor(com.riyaz.rssmoneymanager.R.color.rss_text))
            setPadding(0, 8, 0, 8)
        }

        val create = button("Create Account").apply { isEnabled = false }
        terms.setOnCheckedChangeListener { _, checked -> create.isEnabled = checked }
        create.setOnClickListener {
            val fullName = name.text.toString().trim()
            val address = email.text.toString().trim()
            if (fullName.isEmpty() || address.isEmpty()) {
                Snackbar.make(root, "Enter your full name and email.", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            create.isEnabled = false
            accountExecutor.execute {
                val result = runCatching { accountClient.register(fullName, address, true) }
                    .getOrElse { RssKitAccountClient.Result(false, "RSS KIT account service is temporarily unavailable.") }
                runOnUiThread {
                    create.isEnabled = true
                    if (result.ok && result.verified) {
                        showWelcome()
                    } else if (result.ok && result.verificationPending) {
                        showVerificationPending()
                    } else {
                        Snackbar.make(root, result.message, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }

        val google = button("Continue with Google / Gmail")
        google.setOnClickListener {
            Snackbar.make(root, "Google / Gmail sign-in is provided through the RSS KIT account flow.", Snackbar.LENGTH_LONG).show()
        }

        val existing = textButton("Already have an account? Sign in")
        existing.setOnClickListener {
            showSignIn()
        }

        root.addView(name)
        root.addView(email)
        root.addView(terms)
        root.addView(create)
        root.addView(google)
        root.addView(existing)
        setContentView(root)
    }

    private fun showVerificationPending() {
        root = baseRoot()
        title("Verify your RSS KIT account")
        subtitle("We sent a verification email. You can enter the app after RSS KIT confirms your email.")
        root.addView(icon())
        root.addView(spacer(12))
        root.addView(button("Check Verification Status") {
            accountExecutor.execute {
                val result = runCatching { accountClient.checkStatus() }
                    .getOrElse { RssKitAccountClient.Result(false, "RSS KIT account service is temporarily unavailable.") }
                runOnUiThread {
                    if (result.verified) showWelcome()
                    else Snackbar.make(root, result.message, Snackbar.LENGTH_LONG).show()
                }
            }
        })
        root.addView(button("Resend Verification Email") {
            accountExecutor.execute {
                val result = runCatching { accountClient.resendVerification() }
                    .getOrElse { RssKitAccountClient.Result(false, "RSS KIT account service is temporarily unavailable.") }
                runOnUiThread { Snackbar.make(root, result.message, Snackbar.LENGTH_LONG).show() }
            }
        })
        root.addView(textButton("Sign in with RSS KIT account").apply {
            setOnClickListener { showSignIn() }
        })
        setContentView(root)
    }

    private fun showSignIn() {
        root = baseRoot()
        title("Sign in to RSS Money Manager")
        subtitle("Use the email registered with RSS KIT. Your account state remains server-authoritative.")
        val email = edit("Email", "Email address")
        if (accountClient.email().isNotBlank()) email.setText(accountClient.email())
        root.addView(email)
        root.addView(button("Sign In") {
            val address = email.text.toString().trim()
            if (address.isEmpty()) {
                Snackbar.make(root, "Enter your RSS KIT account email.", Snackbar.LENGTH_SHORT).show()
                return@button
            }
            accountExecutor.execute {
                val result = runCatching {
                    if (accountClient.email() != address) {
                        getSharedPreferences("money_manager_account", MODE_PRIVATE).edit().putString("email", address).apply()
                    }
                    accountClient.createSession()
                }.getOrElse { RssKitAccountClient.Result(false, "RSS KIT account service is temporarily unavailable.") }
                runOnUiThread {
                    if (result.ok && result.verified) showWelcome()
                    else if (result.message.contains("Verify", true)) showVerificationPending()
                    else Snackbar.make(root, result.message, Snackbar.LENGTH_LONG).show()
                }
            }
        })
        root.addView(textButton("Need an account? Create one").apply { setOnClickListener { showRegistration() } })
        setContentView(root)
    }

    private fun showWelcome() {
        page = 1
        root = baseRoot()
        title("Welcome to RSS Money Manager")
        subtitle("A focused money workspace built with the RSS KIT experience: lightweight, animated and professional.")
        root.addView(icon())
        root.addView(spacer(14))
        root.addView(button("Continue") { showFeatures() })
        setContentView(root)
    }

    private fun showFeatures() {
        page = 2
        root = baseRoot()
        title(pages[page].first)
        subtitle(pages[page].second)
        root.addView(featureCard("01", "Dashboard", "Balance, income, expenses and recent activity at a glance."))
        root.addView(featureCard("02", "Transactions", "Income, expenses, transfers, recurring entries and item details."))
        root.addView(featureCard("03", "Wallets", "Organize cash, bank, card, savings and other accounts."))
        root.addView(featureCard("04", "More tools", "Budgets, reports, search, backup, settings and future RSS KIT services."))
        root.addView(button("Enter Money Manager") { finishOnboarding() })
        setContentView(root)
    }

    private fun finishOnboarding() {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETE, true).apply()
        openMain()
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun baseRoot(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), dp(24), dp(24), dp(24))
        setBackgroundColor(getColor(R.color.rss_surface_soft))
    }

    private fun icon(): View = android.widget.ImageView(this).apply {
        setImageResource(R.drawable.rss_money_manager_icon)
        layoutParams = LinearLayout.LayoutParams(dp(112), dp(112)).apply { gravity = android.view.Gravity.CENTER_HORIZONTAL }
        contentDescription = getString(R.string.app_name)
    }

    private fun title(value: String) {
        root.addView(TextView(this).apply {
            text = value
            textSize = 29f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(getColor(R.color.rss_text))
        })
    }

    private fun subtitle(value: String) {
        root.addView(TextView(this).apply {
            text = value
            textSize = 15f
            setTextColor(getColor(R.color.rss_muted))
            setPadding(0, dp(8), 0, dp(16))
        })
    }

    private fun edit(label: String, hint: String) = EditText(this).apply {
        this.hint = "$label — $hint"
        setSingleLine(true)
        setPadding(dp(14), dp(12), dp(14), dp(12))
        layoutParams = LinearLayout.LayoutParams(-1, dp(58)).apply { bottomMargin = dp(10) }
    }

    private fun button(label: String, action: (() -> Unit)? = null) = MaterialButton(this).apply {
        text = label
        isAllCaps = false
        textSize = 15f
        layoutParams = LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(10) }
        action?.let { setOnClickListener { it() } }
    }

    private fun textButton(label: String) = TextView(this).apply {
        text = label
        textSize = 14f
        gravity = android.view.Gravity.CENTER
        setTextColor(getColor(R.color.rss_navy))
        setPadding(0, dp(10), 0, dp(10))
        isClickable = true
    }

    private fun featureCard(number: String, heading: String, body: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        setBackgroundResource(R.drawable.surface_card)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
        addView(TextView(this@OnboardingActivity).apply {
            text = "$number  $heading"
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(getColor(R.color.rss_navy))
        })
        addView(TextView(this@OnboardingActivity).apply {
            text = body
            textSize = 14f
            setTextColor(getColor(R.color.rss_muted))
            setPadding(0, dp(4), 0, 0)
        })
    }

    private fun spacer(dp: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, this@OnboardingActivity.dp(dp)) }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        accountExecutor.shutdownNow()
        super.onDestroy()
    }

    companion object { private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete" }
}
