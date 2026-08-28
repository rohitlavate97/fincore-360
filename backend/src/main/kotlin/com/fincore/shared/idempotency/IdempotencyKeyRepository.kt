package com.fincore.shared.idempotency

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.Optional
import java.util.UUID

@Repository
interface IdempotencyKeyRepository : JpaRepository<IdempotencyKeyRecord, UUID> {
    fun findByKeyAndUserIdAndEndpoint(key: UUID, userId: UUID, endpoint: String): Optional<IdempotencyKeyRecord>

    fun findByKey(key: UUID): Optional<IdempotencyKeyRecord>

    @Modifying
    @Query("DELETE FROM IdempotencyKeyRecord i WHERE i.expiresAt < :now")
    fun deleteAllExpired(@Param("now") now: Instant): Int
}
