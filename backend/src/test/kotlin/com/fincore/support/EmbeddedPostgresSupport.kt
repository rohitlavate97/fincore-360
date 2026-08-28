package com.fincore.support

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import javax.sql.DataSource

/**
 * A single real PostgreSQL instance shared by every database test in the JVM.
 *
 * This is a REAL PostgreSQL server, not an in-memory substitute. TESTING.md §2
 * bans H2 precisely because it cannot prove `SELECT ... FOR UPDATE` semantics,
 * `NUMERIC(19,4)` behaviour, or trigger enforcement — which are exactly the
 * guarantees this system rests on.
 *
 * Testcontainers is the documented long-term choice, but it requires a Docker
 * daemon and this machine has none. When Docker is available this object is the
 * single place that changes.
 */
object EmbeddedPostgresSupport {

    val instance: EmbeddedPostgres by lazy {
        EmbeddedPostgres.builder().start()
    }

    val dataSource: DataSource by lazy { instance.postgresDatabase }
}
