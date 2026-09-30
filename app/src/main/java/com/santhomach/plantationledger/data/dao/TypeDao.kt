package com.santhomach.plantationledger.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.santhomach.plantationledger.data.model.ExpenseSubtype
import com.santhomach.plantationledger.data.model.ExpenseType
import com.santhomach.plantationledger.data.model.IncomeType
import com.santhomach.plantationledger.data.model.PermanentWorker
import com.santhomach.plantationledger.data.model.WorkTask
import com.santhomach.plantationledger.data.model.WorkerType
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseTypeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expenseType: ExpenseType): Long

    @Update
    suspend fun update(expenseType: ExpenseType)

    @Delete
    suspend fun delete(expenseType: ExpenseType)

    @Query("SELECT * FROM expense_types WHERE id = :id")
    suspend fun getById(id: Int): ExpenseType?

    @Query("SELECT * FROM expense_types WHERE isActive = 1 ORDER BY typeName")
    suspend fun getAllActive(): List<ExpenseType>

    @Query("SELECT * FROM expense_types WHERE isActive = 1 ORDER BY typeName")
    fun getAllActiveFlow(): Flow<List<ExpenseType>>

    @Query("SELECT * FROM expense_types ORDER BY typeName")
    suspend fun getAll(): List<ExpenseType>

    @Query("DELETE FROM expense_types")
    suspend fun deleteAll()
}

@Dao
interface ExpenseSubtypeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expenseSubtype: ExpenseSubtype): Long

    @Update
    suspend fun update(expenseSubtype: ExpenseSubtype)

    @Delete
    suspend fun delete(expenseSubtype: ExpenseSubtype)

    @Query("SELECT * FROM expense_subtypes WHERE id = :id")
    suspend fun getById(id: Int): ExpenseSubtype?

    @Query("SELECT * FROM expense_subtypes WHERE typeName = :name LIMIT 1")
    suspend fun getByName(name: String): ExpenseSubtype?

    @Query("SELECT * FROM expense_subtypes WHERE isActive = 1 ORDER BY typeName")
    suspend fun getAllActive(): List<ExpenseSubtype>

    @Query("SELECT * FROM expense_subtypes WHERE isActive = 1 ORDER BY typeName")
    fun getAllActiveFlow(): Flow<List<ExpenseSubtype>>

    @Query("SELECT * FROM expense_subtypes ORDER BY typeName")
    suspend fun getAll(): List<ExpenseSubtype>

    @Query("DELETE FROM expense_subtypes")
    suspend fun deleteAll()
}

@Dao
interface IncomeTypeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(incomeType: IncomeType): Long

    @Update
    suspend fun update(incomeType: IncomeType)

    @Delete
    suspend fun delete(incomeType: IncomeType)

    @Query("SELECT * FROM income_types WHERE id = :id")
    suspend fun getById(id: Int): IncomeType?

    @Query("SELECT * FROM income_types WHERE isActive = 1 ORDER BY typeName")
    suspend fun getAllActive(): List<IncomeType>

    @Query("SELECT * FROM income_types WHERE isActive = 1 ORDER BY typeName")
    fun getAllActiveFlow(): Flow<List<IncomeType>>

    @Query("SELECT * FROM income_types ORDER BY typeName")
    suspend fun getAll(): List<IncomeType>

    @Query("DELETE FROM income_types")
    suspend fun deleteAll()
}

@Dao
interface WorkerTypeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(workerType: WorkerType): Long

    @Update
    suspend fun update(workerType: WorkerType)

    @Delete
    suspend fun delete(workerType: WorkerType)

    @Query("SELECT * FROM worker_types WHERE id = :id")
    suspend fun getById(id: Int): WorkerType?

    @Query("SELECT * FROM worker_types WHERE isActive = 1 ORDER BY workerTypeName")
    suspend fun getAllActive(): List<WorkerType>

    @Query("SELECT * FROM worker_types WHERE isActive = 1 ORDER BY workerTypeName")
    fun getAllActiveFlow(): Flow<List<WorkerType>>

    @Query("SELECT * FROM worker_types ORDER BY workerTypeName")
    suspend fun getAll(): List<WorkerType>
}

@Dao
interface PermanentWorkerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(permanentWorker: PermanentWorker): Long

    @Update
    suspend fun update(permanentWorker: PermanentWorker)

    @Delete
    suspend fun delete(permanentWorker: PermanentWorker)

    @Query("SELECT * FROM permanent_workers WHERE id = :id")
    suspend fun getById(id: Int): PermanentWorker?

    @Query("SELECT * FROM permanent_workers WHERE isActive = 1 ORDER BY name")
    suspend fun getAllActive(): List<PermanentWorker>

    @Query("SELECT * FROM permanent_workers WHERE isActive = 1 ORDER BY name")
    fun getAllActiveFlow(): Flow<List<PermanentWorker>>

    @Query("SELECT * FROM permanent_workers ORDER BY name")
    suspend fun getAll(): List<PermanentWorker>
}

@Dao
interface WorkTaskDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(workTask: WorkTask): Long

    @Update
    suspend fun update(workTask: WorkTask)

    @Delete
    suspend fun delete(workTask: WorkTask)

    @Query("SELECT * FROM work_tasks WHERE id = :id")
    suspend fun getById(id: Int): WorkTask?

    @Query("SELECT * FROM work_tasks WHERE isActive = 1 ORDER BY taskName")
    suspend fun getAllActive(): List<WorkTask>

    @Query("SELECT * FROM work_tasks WHERE isActive = 1 ORDER BY taskName")
    fun getAllActiveFlow(): Flow<List<WorkTask>>

    @Query("SELECT * FROM work_tasks ORDER BY taskName")
    suspend fun getAll(): List<WorkTask>
}
