package com.santhomach.plantationledger.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.santhomach.plantationledger.data.model.WeeklyFunds
import kotlinx.coroutines.flow.Flow

@Dao
interface WeeklyFundsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(weeklyFunds: WeeklyFunds): Long

    @Update
    suspend fun update(weeklyFunds: WeeklyFunds)

    @Delete
    suspend fun delete(weeklyFunds: WeeklyFunds)

    @Query("SELECT * FROM weekly_funds ORDER BY weekStartDate DESC")
    fun getAllFlow(): Flow<List<WeeklyFunds>>

    @Query("SELECT * FROM weekly_funds WHERE weekStartDate = :weekStartDate LIMIT 1")
    suspend fun getByWeek(weekStartDate: String): WeeklyFunds?

    @Query("SELECT * FROM weekly_funds")
    suspend fun getAll(): List<WeeklyFunds>
}
