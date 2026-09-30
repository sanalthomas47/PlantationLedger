package com.santhomach.plantationledger.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.santhomach.plantationledger.data.model.ExcessBalance
import com.santhomach.plantationledger.data.model.WeeklySettlement
import kotlinx.coroutines.flow.Flow

@Dao
interface WeeklySettlementDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(weeklySettlement: WeeklySettlement): Long

    @Update
    suspend fun update(weeklySettlement: WeeklySettlement)

    @Delete
    suspend fun delete(weeklySettlement: WeeklySettlement)

    @Query("SELECT * FROM weekly_settlements WHERE id = :id")
    suspend fun getById(id: Int): WeeklySettlement?

    @Query("SELECT * FROM weekly_settlements WHERE settlementDate = :date")
    suspend fun getBySettlementDate(date: String): WeeklySettlement?

    @Query("SELECT * FROM weekly_settlements WHERE settlementDate BETWEEN :startDate AND :endDate ORDER BY settlementDate DESC")
    suspend fun getByDateRange(startDate: String, endDate: String): List<WeeklySettlement>

    @Query("SELECT * FROM weekly_settlements ORDER BY settlementDate DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 52): List<WeeklySettlement>

    @Query("SELECT * FROM weekly_settlements ORDER BY settlementDate DESC LIMIT :limit")
    fun getRecentFlow(limit: Int = 52): Flow<List<WeeklySettlement>>

    @Query("SELECT * FROM weekly_settlements ORDER BY settlementDate DESC")
    fun getAllFlow(): Flow<List<WeeklySettlement>>

    @Query("DELETE FROM weekly_settlements WHERE id = :id")
    suspend fun deleteById(id: Int)
}

@Dao
interface ExcessBalanceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(excessBalance: ExcessBalance): Long

    @Update
    suspend fun update(excessBalance: ExcessBalance)

    @Delete
    suspend fun delete(excessBalance: ExcessBalance)

    @Query("SELECT * FROM excess_balances WHERE id = :id")
    suspend fun getById(id: Int): ExcessBalance?

    @Query("SELECT * FROM excess_balances WHERE isSettled = 0 ORDER BY date DESC")
    suspend fun getUnsettled(): List<ExcessBalance>

    @Query("SELECT * FROM excess_balances WHERE isSettled = 0 ORDER BY date DESC")
    fun getUnsettledFlow(): Flow<List<ExcessBalance>>

    @Query("SELECT SUM(CASE WHEN direction = 'CREDIT' THEN amount ELSE -amount END) FROM excess_balances WHERE isSettled = 0")
    suspend fun getTotalUnsettledBalance(): String?

    @Query("SELECT * FROM excess_balances WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    suspend fun getByDateRange(startDate: String, endDate: String): List<ExcessBalance>

    @Query("SELECT * FROM excess_balances ORDER BY date DESC")
    fun getAllFlow(): Flow<List<ExcessBalance>>

    @Query("DELETE FROM excess_balances WHERE id = :id")
    suspend fun deleteById(id: Int)
}
