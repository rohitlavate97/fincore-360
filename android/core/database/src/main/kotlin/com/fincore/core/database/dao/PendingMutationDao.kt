package com.fincore.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.fincore.core.database.entity.PendingMutationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingMutationDao {
    @Query("SELECT * FROM pending_mutations WHERE status = 'PENDING' ORDER BY created_at ASC")
    suspend fun getPendingMutations(): List<PendingMutationEntity>

    @Query("SELECT * FROM pending_mutations ORDER BY created_at ASC")
    fun observePendingMutations(): Flow<List<PendingMutationEntity>>

    @Upsert
    suspend fun upsert(mutation: PendingMutationEntity)

    @Query("DELETE FROM pending_mutations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM pending_mutations")
    suspend fun deleteAll()
}
