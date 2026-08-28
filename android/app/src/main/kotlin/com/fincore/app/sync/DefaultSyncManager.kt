package com.fincore.app.sync

import com.fincore.core.common.sync.SyncManager
import com.fincore.core.common.sync.SyncStatus
import com.fincore.core.database.dao.PendingMutationDao
import com.fincore.core.database.dao.SyncMetadataDao
import com.fincore.core.database.entity.SyncMetadataEntity
import com.fincore.core.network.monitor.NetworkMonitor
import com.fincore.feature.accounts.domain.repository.AccountRepository
import com.fincore.feature.transactions.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultSyncManager @Inject constructor(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val syncMetadataDao: SyncMetadataDao,
    private val pendingMutationDao: PendingMutationDao,
    private val networkMonitor: NetworkMonitor
) : SyncManager {

    private val _syncStatus = MutableStateFlow(SyncStatus.IDLE)
    override val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val syncMutex = Mutex()
    private var lastSyncAttemptTime: Long = 0L

    companion object {
        const val FINANCIAL_DATA_SYNC_KEY = "FINANCIAL_DATA"
        const val THROTTLE_INTERVAL_MS = 30_000L
    }

    override fun observeLastSyncTime(syncKey: String): Flow<Long?> {
        return syncMetadataDao.observeSyncMetadata(syncKey).map { it?.lastSyncedAt }
    }

    override suspend fun sync(force: Boolean): Result<Unit> = syncMutex.withLock {
        val now = System.currentTimeMillis()
        if (!force && (now - lastSyncAttemptTime) < THROTTLE_INTERVAL_MS) {
            return Result.success(Unit)
        }

        val isOnline = networkMonitor.isOnline.first()
        if (!isOnline) {
            _syncStatus.value = SyncStatus.ERROR
            return Result.failure(IllegalStateException("Cannot synchronize while offline"))
        }

        lastSyncAttemptTime = now
        _syncStatus.value = SyncStatus.SYNCING

        return runCatching {
            val accountsResult = accountRepository.refreshAccounts()
            if (accountsResult.isFailure) {
                throw accountsResult.exceptionOrNull() ?: RuntimeException("Account sync failed")
            }

            val cachedAccounts = accountRepository.getAccounts().first()
            for (acc in cachedAccounts) {
                transactionRepository.refreshTransactions(acc.id)
            }

            syncMetadataDao.insertSyncMetadata(
                SyncMetadataEntity(
                    syncKey = FINANCIAL_DATA_SYNC_KEY,
                    lastSyncedAt = System.currentTimeMillis(),
                    status = "SUCCESS"
                )
            )

            val pending = pendingMutationDao.getPendingMutations()
            for (mutation in pending) {
                pendingMutationDao.deleteById(mutation.id)
            }

            _syncStatus.value = SyncStatus.SUCCESS
        }.onFailure {
            _syncStatus.value = SyncStatus.ERROR
        }
    }
}
