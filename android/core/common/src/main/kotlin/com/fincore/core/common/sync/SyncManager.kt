package com.fincore.core.common.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class SyncStatus {
    IDLE,
    SYNCING,
    SUCCESS,
    ERROR
}

interface SyncManager {
    val syncStatus: StateFlow<SyncStatus>
    fun observeLastSyncTime(syncKey: String = "FINANCIAL_DATA"): Flow<Long?>
    suspend fun sync(force: Boolean = false): Result<Unit>
}
