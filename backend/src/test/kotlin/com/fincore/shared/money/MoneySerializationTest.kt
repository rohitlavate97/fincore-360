package com.fincore.shared.money

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.json.JsonTest
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.ObjectMapper

/**
 * The transport half of ADR-012.
 *
 * Exactness at rest and in the JVM is undone if the amount crosses the wire as a
 * JSON *number* — a JavaScript client parses that into an IEEE-754 double. These
 * tests pin the amount to a JSON string.
 *
 * Uses the application's own configured ObjectMapper, so this verifies the real
 * serialisation path rather than a hand-built mapper.
 *
 * @JsonTest is a slice: it auto-configures Jackson and nothing else. A full
 * @SpringBootTest here would start the DataSource and Flyway, making a pure
 * serialisation test depend on a running database.
 */
@JsonTest
class MoneySerializationTest {

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    @DisplayName("amount serialises as a JSON STRING, never a number")
    fun amountIsAString() {
        val json = objectMapper.writeValueAsString(Money.of("1234.56", "GBP"))

        // The quotes are the assertion. "amount":1234.56 would be the defect.
        assertTrue(
            json.contains("\"amount\":\"1234.5600\""),
            "amount must be a quoted string, got: $json",
        )
        assertTrue(json.contains("\"currency\":\"GBP\""), "got: $json")
    }

    @Test
    @DisplayName("round trip preserves the exact value and scale")
    fun roundTripIsExact() {
        val original = Money.of("1234.5600", "GBP")
        val json = objectMapper.writeValueAsString(original)
        val restored = objectMapper.readValue(json, Money::class.java)

        assertEquals(original, restored)
        assertEquals("1234.5600", restored.amountAsString())
    }

    @Test
    @DisplayName("deserialisation goes through BigDecimal(String), preserving precision")
    fun deserialisationIsExact() {
        val json = """{"amount":"0.1","currency":"GBP"}"""
        val money = objectMapper.readValue(json, Money::class.java)

        // If this went via a double, 0.1 would become 0.1000000000000000055511...
        assertEquals("0.1000", money.amountAsString())
    }

    @Test
    @DisplayName("a value that would lose precision as a double survives")
    fun highPrecisionValueSurvives() {
        val json = """{"amount":"9007199254740993.0001","currency":"GBP"}"""
        val money = objectMapper.readValue(json, Money::class.java)
        assertEquals("9007199254740993.0001", money.amountAsString())
    }
}
