package com.riyaz.rssmoneymanager

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.riyaz.rssmoneymanager.databinding.ActivityRssKitMenuBinding

class RssKitMenuActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRssKitMenuBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRssKitMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.menuClose.setOnClickListener { finish() }
        binding.menuHome.setOnClickListener { finish() }
        binding.menuTransactions.setOnClickListener { openMain("transactions") }
        binding.menuAccounts.setOnClickListener { openMain("accounts") }
        binding.menuMore.setOnClickListener { openMain("more") }
        binding.menuSettings.setOnClickListener { showPage(binding.settingsPage) }
        binding.menuAbout.setOnClickListener { showPage(binding.aboutPage) }
        binding.menuContact.setOnClickListener { showPage(binding.contactPage) }
        binding.menuPrivacy.setOnClickListener { showPage(binding.privacyPage) }
        binding.menuTerms.setOnClickListener { showPage(binding.termsPage) }
        binding.pageBackSettings.setOnClickListener { showMenu() }
        binding.pageBackAbout.setOnClickListener { showMenu() }
        binding.pageBackContact.setOnClickListener { showMenu() }
        binding.pageBackPrivacy.setOnClickListener { showMenu() }
        binding.pageBackTerms.setOnClickListener { showMenu() }

        binding.themeSystem.setOnClickListener { saveTheme("system") }
        binding.themeLight.setOnClickListener { saveTheme("light") }
        binding.themeDark.setOnClickListener { saveTheme("dark") }
        binding.clearLocalData.setOnClickListener {
            getSharedPreferences("money_manager", MODE_PRIVATE).edit().clear().apply()
            binding.settingsStatus.text = "Local Money Manager data cleared."
        }
    }

    private fun saveTheme(theme: String) {
        getSharedPreferences("money_manager", MODE_PRIVATE).edit().putString("theme_mode", theme).apply()
        binding.settingsStatus.text = "Theme preference saved: " + theme.replaceFirstChar { it.uppercase() }
    }

    private fun openMain(screen: String) {
        startActivity(Intent(this, MainActivity::class.java).putExtra("open_screen", screen))
        finish()
    }

    private fun showMenu() {
        binding.drawerPanel.visibility = View.VISIBLE
        binding.settingsPage.visibility = View.GONE
        binding.aboutPage.visibility = View.GONE
        binding.contactPage.visibility = View.GONE
        binding.privacyPage.visibility = View.GONE
        binding.termsPage.visibility = View.GONE
        binding.drawerPanel.translationX = -32f
        binding.drawerPanel.alpha = 0.92f
        binding.drawerPanel.animate().translationX(0f).alpha(1f).setDuration(220L).start()
    }

    private fun showPage(page: View) {
        binding.drawerPanel.visibility = View.GONE
        listOf(binding.settingsPage, binding.aboutPage, binding.contactPage, binding.privacyPage, binding.termsPage)
            .forEach { it.visibility = if (it === page) View.VISIBLE else View.GONE }
        page.alpha = 0f
        page.translationX = 24f
        page.animate().alpha(1f).translationX(0f).setDuration(220L).start()
    }
}
