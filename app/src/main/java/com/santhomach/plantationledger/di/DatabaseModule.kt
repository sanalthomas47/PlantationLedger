package com.santhomach.plantationledger.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.santhomach.plantationledger.data.AppDatabase
import com.santhomach.plantationledger.data.backup.AndroidBackupStore
import com.santhomach.plantationledger.data.backup.BackupStore
import com.santhomach.plantationledger.data.backup.DailyBackupManager
import com.santhomach.plantationledger.data.export.ExportManager
import com.santhomach.plantationledger.data.repository.ExpenseRepository
import com.santhomach.plantationledger.data.schedule.ScheduleDatabase
import com.santhomach.plantationledger.data.schedule.ScheduleRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideExpenseRepository(database: AppDatabase): ExpenseRepository {
        return ExpenseRepository(database)
    }

    @Provides
    @Singleton
    fun provideExportManager(
        @ApplicationContext context: Context,
        scheduleRepository: ScheduleRepository
    ): ExportManager {
        return ExportManager(context) { scheduleRepository }
    }

    @Provides
    @Singleton
    fun provideScheduleRepository(@ApplicationContext context: Context): ScheduleRepository {
        return ScheduleRepository(ScheduleDatabase.getInstance(context).scheduleDao())
    }

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.appDataStore
    }

    @Provides
    @Singleton
    fun provideDailyBackupManager(
        @ApplicationContext context: Context,
        exportManager: ExportManager,
        dataStore: DataStore<Preferences>
    ): DailyBackupManager {
        return DailyBackupManager(context, exportManager, dataStore)
    }

    @Provides
    @Singleton
    fun provideBackupStore(@ApplicationContext context: Context): BackupStore {
        return AndroidBackupStore(context)
    }
}
