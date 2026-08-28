package com.fincore.schema

import com.fincore.support.EmbeddedPostgresSupport
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import javax.sql.DataSource

/**
 * Runs the real Flyway migrations against a real PostgreSQL server and asserts
 * the guarantees the schema is supposed to carry.
 *
 * These are the tests that convert DATABASE-DESIGN.md from a claim into a fact.
 */
class SchemaMigrationTest {

    companion object {
        private lateinit var dataSource: DataSource

        @JvmStatic
        @BeforeAll
        fun migrate() {
            dataSource = EmbeddedPostgresSupport.dataSource
            Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load()
                .also { it.clean() }
                .migrate()
        }
    }

    private fun <T> query(sql: String, extract: (java.sql.ResultSet) -> T): T =
        dataSource.connection.use { conn ->
            conn.createStatement().use { st ->
                st.executeQuery(sql).use { rs -> extract(rs) }
            }
        }

    @Test
    @DisplayName("migrations apply cleanly from an empty database")
    fun migrationsApply() {
        val tables = query(
            """
            SELECT table_name FROM information_schema.tables
             WHERE table_schema = 'public' ORDER BY table_name
            """,
        ) { rs -> generateSequence { if (rs.next()) rs.getString(1) else null }.toList() }

        listOf(
            "accounts", "audit_events", "customers",
            "idempotency_keys", "refresh_tokens", "transactions",
        ).forEach { expected ->
            assertTrue(tables.contains(expected), "missing table '$expected'; found $tables")
        }
    }

    @Test
    @DisplayName("every monetary column is NUMERIC(19,4) — ADR-012 enforced at the schema")
    fun monetaryColumnsArePrecise() {
        val money = query(
            """
            SELECT table_name, column_name, numeric_precision, numeric_scale
              FROM information_schema.columns
             WHERE table_schema = 'public'
               AND column_name IN ('amount', 'ledger_balance', 'available_balance')
            """,
        ) { rs ->
            generateSequence {
                if (rs.next()) {
                    listOf(rs.getString(1), rs.getString(2), rs.getInt(3).toString(), rs.getInt(4).toString())
                } else {
                    null
                }
            }.toList()
        }

        assertTrue(money.isNotEmpty(), "no monetary columns found — schema did not apply")
        money.forEach { (table, column, precision, scale) ->
            assertEquals("19", precision, "$table.$column precision")
            assertEquals("4", scale, "$table.$column scale")
        }
    }

    @Test
    @DisplayName("no column anywhere uses a floating point type")
    fun noFloatingPointColumns() {
        val floats = query(
            """
            SELECT table_name, column_name, data_type
              FROM information_schema.columns
             WHERE table_schema = 'public'
               AND data_type IN ('real', 'double precision')
            """,
        ) { rs ->
            generateSequence { if (rs.next()) "${rs.getString(1)}.${rs.getString(2)}" else null }.toList()
        }

        assertTrue(floats.isEmpty(), "floating point columns found: $floats")
    }

    @Test
    @DisplayName("audit_events rejects UPDATE — append-only enforced by the database")
    fun auditRejectsUpdate() {
        insertAuditEvent()

        val error = runCatching {
            dataSource.connection.use { conn ->
                conn.createStatement().use { it.executeUpdate("UPDATE audit_events SET outcome = 'FAILURE'") }
            }
        }.exceptionOrNull()

        assertTrue(error != null, "UPDATE on audit_events should have been rejected but succeeded")
        assertTrue(
            error!!.message!!.contains("append-only"),
            "expected the append-only trigger to fire, got: ${error.message}",
        )
    }

    @Test
    @DisplayName("audit_events rejects DELETE — an editable audit log proves nothing")
    fun auditRejectsDelete() {
        insertAuditEvent()

        val error = runCatching {
            dataSource.connection.use { conn ->
                conn.createStatement().use { it.executeUpdate("DELETE FROM audit_events") }
            }
        }.exceptionOrNull()

        assertTrue(error != null, "DELETE on audit_events should have been rejected but succeeded")
        assertTrue(
            error!!.message!!.contains("append-only"),
            "expected the append-only trigger to fire, got: ${error.message}",
        )
    }

    @Test
    @DisplayName("available_balance cannot go negative — the invariant the locking design protects")
    fun availableBalanceCannotGoNegative() {
        dataSource.connection.use { conn ->
            conn.createStatement().use {
                it.executeUpdate(
                    """
                    INSERT INTO customers (id, email, full_name, status, updated_at)
                    VALUES ('11111111-1111-1111-1111-111111111111', 'a@example.test', 'Test Customer', 'ACTIVE', now())
                    ON CONFLICT DO NOTHING
                    """,
                )
            }
        }

        val error = runCatching {
            dataSource.connection.use { conn ->
                conn.createStatement().use {
                    it.executeUpdate(
                        """
                        INSERT INTO accounts
                            (id, customer_id, account_number, account_type, status, currency,
                             ledger_balance, available_balance, updated_at)
                        VALUES ('22222222-2222-2222-2222-222222222222',
                                '11111111-1111-1111-1111-111111111111',
                                'ACC-NEG-1', 'CHECKING', 'ACTIVE', 'GBP', 0, -0.0001, now())
                        """,
                    )
                }
            }
        }.exceptionOrNull()

        assertTrue(error != null, "a negative available_balance should have been rejected")
    }

    private fun insertAuditEvent() {
        dataSource.connection.use { conn ->
            conn.createStatement().use {
                it.executeUpdate(
                    """
                    INSERT INTO audit_events (event_id, event_type, outcome)
                    VALUES (gen_random_uuid(), 'LOGIN', 'SUCCESS')
                    """,
                )
            }
        }
    }
}
