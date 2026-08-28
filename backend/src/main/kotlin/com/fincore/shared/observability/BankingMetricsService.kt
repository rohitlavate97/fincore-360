package com.fincore.shared.observability

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class BankingMetricsService(
    private val meterRegistry: MeterRegistry
) {

    fun recordTransferInitiated(currency: String) {
        Counter.builder("fincore.transfers.initiated")
            .description("Total number of transfer attempts initiated")
            .tag("currency", currency.uppercase())
            .register(meterRegistry)
            .increment()
    }

    fun recordTransferCompleted(currency: String, durationMillis: Long) {
        Counter.builder("fincore.transfers.completed")
            .description("Total number of transfers successfully executed and committed")
            .tag("currency", currency.uppercase())
            .register(meterRegistry)
            .increment()

        Timer.builder("fincore.transfer.duration")
            .description("Execution duration for completed balance transfers")
            .tag("currency", currency.uppercase())
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry)
            .record(durationMillis, TimeUnit.MILLISECONDS)
    }

    fun recordTransferFailed(currency: String, reason: String) {
        Counter.builder("fincore.transfers.failed")
            .description("Total number of failed transfer attempts categorized by failure reason")
            .tag("currency", currency.uppercase())
            .tag("reason", reason)
            .register(meterRegistry)
            .increment()
    }

    fun recordIdempotencyReplay(endpoint: String = "/api/v1/transfers") {
        Counter.builder("fincore.idempotency.replays")
            .description("Total number of idempotent request replays served from cache")
            .tag("endpoint", endpoint)
            .register(meterRegistry)
            .increment()
    }
}
