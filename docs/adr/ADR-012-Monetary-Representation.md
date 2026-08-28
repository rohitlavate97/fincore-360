# ADR-012: Monetary representation — BigDecimal, NUMERIC(19,4), string in JSON

**Status:** Accepted
**Date:** 2026-08-28
**Decided in phase:** 0
**Implemented in phase:** 1 (schema and types), 5 (arithmetic)

---

## Context

Binary floating point cannot represent most decimal fractions exactly. `0.1` has
no exact IEEE-754 representation, so:

```
0.1 + 0.2 == 0.30000000000000004
```

In a ledger this is not a rounding curiosity. Balances drift, debits and credits
fail to reconcile, and the discrepancy compounds across transactions. There is no
amount of rounding-at-the-edges that repairs it, because the error is introduced
by the storage type itself.

The end-to-end chain matters as much as the type. A value can be exact in the
database, exact in the JVM, and still be destroyed on the way to the client:
`JSON.parse("0.1")` in JavaScript produces an IEEE-754 double, and JSON numbers
have no defined precision.

## Decision

Money is represented as follows, with **no exceptions anywhere in the system**:

| Layer | Representation |
|---|---|
| Backend (Kotlin/JVM) | `java.math.BigDecimal` |
| PostgreSQL | `NUMERIC(19,4)` |
| JSON transport | **string** — `"1234.5600"` |
| Android (Kotlin) | `BigDecimal`, parsed from the string |
| Web (TypeScript) | `string`, formatted with `Intl.NumberFormat`; arithmetic via a decimal library, never `number` |
| Currency | `CHAR(3)`, ISO 4217 |

**Rules:**

- `double` and `float` are **banned** for monetary values at every layer.
- Never construct `BigDecimal` from a `double` — `BigDecimal(0.1)` inherits the
  binary error. Use `BigDecimal("0.1")` or `BigDecimal.valueOf`.
- Every division specifies scale and a `RoundingMode` explicitly. An unspecified
  division that does not terminate throws `ArithmeticException`.
- Compare with `compareTo`, never `equals` — `BigDecimal("1.0").equals(BigDecimal("1.00"))`
  is `false`, because `equals` compares scale.
- Amount and currency travel together. A bare amount is meaningless.

**Why scale 4 rather than 2:** intermediate results — interest, fee
apportionment, FX — need precision below the minor unit before a final rounding
step. Storing at 2 forces premature rounding.

**Why precision 19:** comfortably exceeds any simulated balance while remaining
within the range where `NUMERIC` performs well.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| `double` / `float` | Cannot represent decimal currency exactly. This is the defect the ADR exists to prevent. |
| `long` of minor units (pence/cents) | Genuinely good, and used by several real ledgers — exact, fast, no rounding surprises. Rejected because it makes sub-minor-unit intermediates (fee splits, interest) awkward, hides currency exponent differences (JPY has 0 decimals, KWD has 3), and forces every boundary to remember the scale. `NUMERIC`/`BigDecimal` keeps scale explicit in the type. |
| JSON **number** for amounts | The precision is lost in the client, not the server. A JavaScript consumer silently converts to a double, so correctness at rest is undone in transit. |
| Database `NUMERIC`, JVM `double` | Reintroduces the error at the arithmetic layer, which is where it does the most damage. |

## Consequences

### Positive

- Arithmetic is exact end to end; balances reconcile against the transaction
  ledger.
- Scale and rounding are explicit and reviewable rather than emergent.
- A JSON string survives every client language unchanged.

### Negative — what this costs us

- **`BigDecimal` arithmetic is slower** than primitives, and allocates. This is
  accepted; correctness is not negotiable here.
- **`BigDecimal` is easy to misuse.** `equals` versus `compareTo` and the
  `double` constructor are both traps that produce subtly wrong results rather
  than errors, which makes them worse than crashes.
- String amounts mean every client must parse before arithmetic, and a client
  that forgets and does `parseFloat` reintroduces the whole problem outside our
  control. This is a real residual risk, mitigated only by API documentation and
  the web portal's own decimal library.
- Amounts as strings are less convenient in ad-hoc tooling and dashboards.

### Neutral / follow-on work

- A shared `Money` value object (amount + currency) should be introduced in
  Phase 1 so the pairing is structural rather than conventional.
- A static-analysis or ArchUnit rule banning `double`/`float` in financial
  packages is worth more than the convention alone.

## Verification

- Schema assertion: every monetary column is `NUMERIC(19,4)`.
- Contract test: amounts serialise as JSON strings, not numbers.
- Arithmetic test: repeated additions of `0.1` sum exactly; a debit/credit cycle
  returns the balance to its starting value.
- ArchUnit rule: no `double`/`float` field in `com.fincore.*` financial types.

> `NOT VERIFIED — no schema, types, serialisation config, or tests exist. This is
> currently a rule with nothing enforcing it.`

## Interview notes

**The trap:** "We use `BigDecimal` for money." Correct and incomplete — it
addresses one layer of a four-layer chain, and the layer most people lose is
transport.

**The senior answer:** State the full chain: `BigDecimal` in the JVM,
`NUMERIC(19,4)` at rest, and **string** in JSON. The last one is the part that
gets missed — if you serialise an amount as a JSON number, a JavaScript client
parses it into an IEEE-754 double and you have destroyed in transit exactly the
precision you protected everywhere else. Then show you know `BigDecimal`'s own
traps: `new BigDecimal(0.1)` inherits the binary error you were avoiding, and
`equals` compares scale so `1.0` does not equal `1.00` — you compare with
`compareTo`. If asked about integer minor units, acknowledge it is a legitimate
alternative used in real ledgers; the trade is exactness and speed against
awkward sub-minor-unit intermediates and per-currency exponents.
