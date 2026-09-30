package com.santhomach.plantationledger.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.santhomach.plantationledger.data.model.VendorPayment
import kotlinx.coroutines.flow.Flow

@Dao
interface VendorPaymentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vendorPayment: VendorPayment): Long

    @Delete
    suspend fun delete(vendorPayment: VendorPayment)

    @Query("SELECT * FROM vendor_payments ORDER BY date DESC")
    fun getAllFlow(): Flow<List<VendorPayment>>

    @Query("SELECT * FROM vendor_payments ORDER BY date DESC")
    suspend fun getAll(): List<VendorPayment>

    @Query("SELECT * FROM vendor_payments WHERE sourceExpenseDate = :date ORDER BY createdAt DESC")
    fun getBySourceExpenseDateFlow(date: String): Flow<List<VendorPayment>>

    @Query("SELECT * FROM vendor_payments WHERE sourceExpenseDate = :date")
    suspend fun getAllBySourceExpenseDate(date: String): List<VendorPayment>

    @Query("SELECT * FROM vendor_payments WHERE date = :date ORDER BY createdAt DESC")
    fun getByPaymentDateFlow(date: String): Flow<List<VendorPayment>>

    @Query("SELECT * FROM vendor_payments WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun getByPaymentDateRangeFlow(startDate: String, endDate: String): Flow<List<VendorPayment>>
}
