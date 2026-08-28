package com.fincore.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.fincore.core.database.dao.AccountDao
import com.fincore.core.database.dao.NotificationDao
import com.fincore.core.database.dao.PendingMutationDao
import com.fincore.core.database.dao.SyncMetadataDao
import com.fincore.core.database.dao.TransactionDao
import com.fincore.core.database.entity.AccountEntity
import com.fincore.core.database.entity.NotificationEntity
import com.fincore.core.database.entity.PendingMutationEntity
import com.fincore.core.database.entity.SyncMetadataEntity
import com.fincore.core.database.entity.TransactionEntity

@Database(
    entities = [
        SyncMetadataEntity::class,
        AccountEntity::class,
        TransactionEntity::class,
        PendingMutationEntity::class,
        NotificationEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class FinCoreDatabase : RoomDatabase() {
    abstract fun syncMetadataDao(): SyncMetadataDao
    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun pendingMutationDao(): PendingMutationDao
    abstract fun notificationDao(): NotificationDao
}
