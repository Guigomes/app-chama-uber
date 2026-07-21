package com.defy.notivault.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UberCallDao {

    @Insert
    suspend fun insert(call: UberCallEntity)

    @Query("SELECT * FROM uber_calls ORDER BY calledAt DESC")
    fun getAllOrderedByRecent(): Flow<List<UberCallEntity>>

    @Query("DELETE FROM uber_calls")
    suspend fun deleteAll()
}
