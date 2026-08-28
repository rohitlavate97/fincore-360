package com.fincore.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.fincore.core.database.dao.AccountDao
import com.fincore.core.database.dao.SyncMetadataDao
import com.fincore.core.database.dao.TransactionDao
import com.fincore.core.database.entity.AccountEntity
import com.fincore.core.database.entity.SyncMetadataEntity
import com.fincore.core.database.entity.TransactionEntity

@Database(
    entities = [SyncMetadataEntity::class, AccountEntity::class, TransactionEntity::class],
    version = 3,
    exportSchema = false
)
abstract class FinCoreDatabase : RoomDatabase() {
    abstract fun syncMetadataDao(): SyncMetadataDao
    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
}
