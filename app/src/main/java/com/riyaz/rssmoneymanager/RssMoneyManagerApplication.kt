package com.riyaz.rssmoneymanager

import android.app.Application
import android.content.SharedPreferences
import java.util.concurrent.Executors

class RssMoneyManagerApplication : Application() {
    private val executor = Executors.newSingleThreadExecutor()
    private var restoring = false

    override fun onCreate() {
        super.onCreate()

        val prefs = getSharedPreferences("money_manager", MODE_PRIVATE)
        val dao = AppDatabase.getInstance(this).appStateDao()

        try {
            executor.submit {
                val roomState = dao.getState()
                if (roomState != null) {
                    restoring = true
                    prefs.edit()
                        .putLong("balanceMinor", roomState.balanceMinor)
                        .putLong("incomeMinor", roomState.incomeMinor)
                        .putLong("expenseMinor", roomState.expenseMinor)
                        .putString("transactions", roomState.transactionsJson)
                        .commit()
                    restoring = false
                } else {
                    migratePreferencesToRoom(prefs, dao)
                }
            }.get()
        } catch (_: Exception) {
            // Keep the existing SharedPreferences state if Room cannot initialize.
        }

        prefs.registerOnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (!restoring && key in PERSISTED_KEYS) {
                executor.execute { migratePreferencesToRoom(sharedPreferences, dao) }
            }
        }
    }

    private fun migratePreferencesToRoom(
        prefs: SharedPreferences,
        dao: AppStateDao
    ) {
        val transactions = prefs.getString("transactions", "[]") ?: "[]"
        dao.upsertState(
            AppStateEntity(
                balanceMinor = prefs.getLong("balanceMinor", 0L),
                incomeMinor = prefs.getLong("incomeMinor", 0L),
                expenseMinor = prefs.getLong("expenseMinor", 0L),
                transactionsJson = transactions
            )
        )
    }

    override fun onTerminate() {
        executor.shutdown()
        super.onTerminate()
    }

    companion object {
        private val PERSISTED_KEYS = setOf("balanceMinor", "incomeMinor", "expenseMinor", "transactions")
    }
}
