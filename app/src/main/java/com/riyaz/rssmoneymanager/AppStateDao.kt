package com.riyaz.rssmoneymanager

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface AppStateDao {
    @Query("SELECT * FROM app_state WHERE id = 1 LIMIT 1")
    fun getState(): AppStateEntity?

    @Upsert
    fun upsertState(state: AppStateEntity)
}
