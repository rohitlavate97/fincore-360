package com.fincore.shared.idempotency

import com.fincore.shared.error.ConflictException
import com.fincore.shared.error.IdempotencyInProgressException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

sealed interface IdempotencyResolution {
    data class Proceed(val record: IdempotencyKeyRecord) : IdempotencyResolution
    data class Replay(val status: Int, val body: String) : IdempotencyResolution
}

@Service
class IdempotencyService(
    private val repository: IdempotencyKeyRepository
) {
    companion object {
        val DEFAULT_EXPIRY_DURATION: Duration = Duration.ofHours(24)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun startOrResolve(
        key: UUID,
        userId: UUID,
        endpoint: String,
        ttl: Duration = DEFAULT_EXPIRY_DURATION
    ): IdempotencyResolution {
        val existingGlobal = repository.findByKey(key)
        if (existingGlobal.isPresent) {
            val record = existingGlobal.get()
            if (record.userId != userId || record.endpoint != endpoint) {
                throw ConflictException("Idempotency key belongs to another operation or user")
            }
            return when (record.state) {
                IdempotencyState.COMPLETE -> IdempotencyResolution.Replay(
                    record.responseStatus ?: 200,
                    record.responseBody ?: ""
                )
                IdempotencyState.IN_PROGRESS -> throw IdempotencyInProgressException()
            }
        }

        val newRecord = IdempotencyKeyRecord(
            id = UUID.randomUUID(),
            key = key,
            userId = userId,
            endpoint = endpoint,
            state = IdempotencyState.IN_PROGRESS,
            expiresAt = Instant.now().plus(ttl)
        )

        return try {
            val saved = repository.saveAndFlush(newRecord)
            IdempotencyResolution.Proceed(saved)
        } catch (e: DataIntegrityViolationException) {
            val winner = repository.findByKeyAndUserIdAndEndpoint(key, userId, endpoint)
                .orElseThrow { ConflictException("Idempotency conflict detected") }

            when (winner.state) {
                IdempotencyState.COMPLETE -> IdempotencyResolution.Replay(
                    winner.responseStatus ?: 200,
                    winner.responseBody ?: ""
                )
                IdempotencyState.IN_PROGRESS -> throw IdempotencyInProgressException()
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun complete(
        recordId: UUID,
        status: Int,
        responseBody: String
    ) {
        val record = repository.findById(recordId).orElse(null) ?: return
        record.state = IdempotencyState.COMPLETE
        record.responseStatus = status
        record.responseBody = responseBody
        repository.saveAndFlush(record)
    }
}
