package com.fincore.shared.outbox

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["fincore.outbox.relay.enabled"], havingValue = "true", matchIfMissing = false)
class OutboxRelayScheduler(
    private val outboxService: OutboxService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${fincore.outbox.relay.interval-ms:2000}")
    fun relay() {
        runCatching { outboxService.relayPendingEvents() }
            .onFailure { log.error("Outbox relay cycle failed: {}", it.message, it) }
    }
}
