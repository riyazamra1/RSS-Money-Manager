package com.riyaz.rssmoneymanager

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_state")
data class AppStateEntity(
    @PrimaryKey val id: Int = 1,
    val balanceMinor: Long,
    val incomeMinor: Long,
    val expenseMinor: Long,
    val transactionsJson: String
)
