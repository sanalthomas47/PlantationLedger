package com.santhomach.plantationledger.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.santhomach.plantationledger.data.model.DailyExpense
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyExpenseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(dailyExpense: DailyExpense): Long

    @Update
    suspend fun update(dailyExpense: DailyExpense)

    @Delete
    suspend fun delete(dailyExpense: DailyExpense)

    @Query("SELECT * FROM daily_expenses WHERE id = :id")
    suspend fun getById(id: Int): DailyExpense?

    @Query("SELECT * FROM daily_expenses WHERE date = :date ORDER BY createdAt DESC")
    suspend fun getByDate(date: String): List<DailyExpense>

    @Query("SELECT * FROM daily_expenses WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    suspend fun getByDateRange(startDate: String, endDate: String): List<DailyExpense>

    @Query("SELECT * FROM daily_expenses WHERE date = :date")
    fun getByDateFlow(date: String): Flow<List<DailyExpense>>

    @Query("SELECT * FROM daily_expenses WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun getByDateRangeFlow(startDate: String, endDate: String): Flow<List<DailyExpense>>

    @Query("SELECT * FROM daily_expenses ORDER BY date DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 50): List<DailyExpense>

    @Query("SELECT * FROM daily_expenses ORDER BY date DESC LIMIT :limit")
    fun getRecentFlow(limit: Int = 50): Flow<List<DailyExpense>>

    @Query("SELECT COUNT(*) FROM daily_expenses")
    suspend fun getCount(): Int

    @Query("SELECT * FROM daily_expenses WHERE managerId = :managerId ORDER BY date DESC")
    suspend fun getByManager(managerId: Int): List<DailyExpense>

    @Query("SELECT * FROM daily_expenses WHERE date < :date ORDER BY date ASC")
    suspend fun getBeforeDate(date: String): List<DailyExpense>

    @Query(
        """
        SELECT * FROM daily_expenses
        WHERE comments LIKE '%' || :query || '%'
        OR advanceReason LIKE '%' || :query || '%'
        OR otherExpenses LIKE '%' || :query || '%'
        OR incomeEntries LIKE '%' || :query || '%'
        OR workerGroups LIKE '%' || :query || '%'
        OR advanceEntries LIKE '%' || :query || '%'
        ORDER BY date DESC
    """
    )
    suspend fun searchExpenses(query: String): List<DailyExpense>

    @Query("DELETE FROM daily_expenses WHERE date = :date")
    suspend fun deleteByDate(date: String)
}
