package com.fincore.shared.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * The OpenAPI spec is generated from the implementation, so it cannot drift from
 * the code. It is the shared contract of record between backend, Android, and
 * the web portal — each validates against it rather than against the others.
 * See API-DESIGN.md §9.
 */
@Configuration
class OpenApiConfig {

    @Bean
    fun fincoreOpenApi(): OpenAPI =
        OpenAPI().info(
            Info()
                .title("FinCore 360 API")
                .version("v1")
                .description(
                    """
                    Enterprise digital banking **simulation** platform.

                    This API processes no real money and integrates no banking or
                    payment rails. All users, accounts, balances, and transactions
                    are fictional.

                    Conventions:
                    - Monetary amounts are JSON **strings** with scale 4, always
                      paired with an ISO 4217 currency. Never JSON numbers — a
                      JSON number is parsed into an IEEE-754 double by JavaScript
                      clients, losing precision.
                    - Every request accepts `X-Correlation-ID`; it is echoed on
                      the response and appears as `traceId` in error bodies.
                    - Every state-mutating request requires `Idempotency-Key`.
                    """.trimIndent(),
                )
                .license(License().name("Educational / portfolio use only")),
        )
}
