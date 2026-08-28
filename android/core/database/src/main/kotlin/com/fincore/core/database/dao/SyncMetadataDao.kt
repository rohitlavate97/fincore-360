package com.fincore.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fincore.core.database.entity.SyncMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncMetadataDao {
    @Query("SELECT * FROM sync_metadata WHERE syncKey = :key")
    suspend fun getSyncMetadata(key: String): SyncMetadataEntity?

    @Query("SELECT * FROM sync_metadata WHERE syncKey = :key")
    fun observeSyncMetadata(key: String): Flow<SyncMetadataEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncMetadata(metadata: SyncMetadataEntity)

    @Query("DELETE FROM sync_metadata WHERE syncKey = :key")
    suspend fun deleteByKey(key: String)
}
