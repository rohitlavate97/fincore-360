package com.fincore.core.database.di

import android.content.Context
import androidx.room.Room
import com.fincore.core.database.FinCoreDatabase
import com.fincore.core.database.dao.AccountDao
import com.fincore.core.database.dao.PendingMutationDao
import com.fincore.core.database.dao.SyncMetadataDao
import com.fincore.core.database.dao.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FinCoreDatabase {
        return Room.databaseBuilder(
            context,
            FinCoreDatabase::class.java,
            "fincore.db"
        )
            .fallbackToDestructiveMigration(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideSyncMetadataDao(database: FinCoreDatabase): SyncMetadataDao {
        return database.syncMetadataDao()
    }

    @Provides
    @Singleton
    fun provideAccountDao(database: FinCoreDatabase): AccountDao {
        return database.accountDao()
    }

    @Provides
    @Singleton
    fun provideTransactionDao(database: FinCoreDatabase): TransactionDao {
        return database.transactionDao()
    }

    @Provides
    @Singleton
    fun providePendingMutationDao(database: FinCoreDatabase): PendingMutationDao {
        return database.pendingMutationDao()
    }

    @Provides
    @Singleton
    fun provideNotificationDao(database: FinCoreDatabase): com.fincore.core.database.dao.NotificationDao {
        return database.notificationDao()
    }
}
