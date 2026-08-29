package com.fincore.shared.idempotency

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Scheduled purge job for expired idempotency keys (M-3).
 * Keeps idempotency_keys bounded to prevent table bloat on high-throughput mutation paths.
 */
@Component
class IdempotencyKeyPurgeJob(
    private val idempotencyKeyRepository: IdempotencyKeyRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${fincore.idempotency.purge-cron:0 0 3 * * *}")
    @Transactional
    fun purgeExpiredKeys(): Int {
        val now = Instant.now()
        val deletedCount = idempotencyKeyRepository.deleteAllExpired(now)
        if (deletedCount > 0) {
            log.info("Purged {} expired idempotency keys older than {}", deletedCount, now)
        }
        return deletedCount
    }
}
