# ADR-016: kotlinx.serialization for Android, Jackson for the backend

**Status:** Accepted
**Date:** 2026-08-28
**Decided in phase:** 0
**Implemented in phase:** 1 (backend), 2 (Android)

---

## Context

The master prompt requires justifying "Kotlin Serialization or Moshi" on Android.
The backend needs the same decision made independently, because the constraints
differ: Spring Boot's entire ecosystem — Spring Web, Spring Security, Springdoc
OpenAPI — assumes Jackson.

One shared requirement dominates both sides: **monetary amounts are transported
as JSON strings and must deserialise into `BigDecimal`** (ADR-012). A serialiser
that quietly turns `"1234.5600"` into a `Double`, or emits a `BigDecimal` as a
JSON number, silently destroys the precision guarantee the whole system is built
on. This is the deciding constraint, not ergonomics.

## Decision

**Android: kotlinx.serialization.** **Backend: Jackson.**

The asymmetry is deliberate. Fighting Spring's Jackson assumption buys nothing,
while Android benefits from compile-time codegen.

**Both sides must implement and test a custom `Money` converter:**

| Direction | Required behaviour |
|---|---|
| Serialise | `BigDecimal` → JSON **string**, fixed scale 4 |
| Deserialise | JSON string → `BigDecimal` via the `String` constructor — never via `double` |

On the backend this additionally requires disabling any configuration that would
write `BigDecimal` as a JSON number, and asserting it in a contract test.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| **Moshi** (Android) | Solid, and its codegen avoids reflection like kotlinx does. Rejected narrowly: kotlinx is Kotlin-first with no adapter needed for default values or nullability, and `@Serializable` sealed hierarchies map directly onto the `ScreenState`/error types. Moshi would have been an acceptable choice. |
| **Gson** (Android) | Reflection-based, ignores Kotlin nullability entirely — it will happily place `null` in a non-null field, producing an NPE far from the cause. Disqualifying for a client parsing untrusted responses. |
| **Jackson on Android** | Method count and startup cost are meaningful on mobile; Kotlin support requires an extra module. |
| **kotlinx.serialization on the backend** | Possible with Spring, but swims against Springdoc, Spring Security's JSON handling, and most Spring documentation. Cost without benefit. |
| Sharing one serialiser across both | Would force one side into a poor fit purely for symmetry. The contract is the JSON, not the library. |

## Consequences

### Positive

- kotlinx generates serialisers at compile time — no reflection, smaller runtime
  cost, and a missing `@Serializable` is a build error rather than a crash.
- Kotlin defaults, nullability, and sealed classes are handled natively on the
  client.
- Jackson on the backend means Springdoc, Spring Security, and error handling all
  work as documented.

### Negative — what this costs us

- **Two serialisation configurations to keep consistent.** The JSON contract is
  now maintained in two places, and drift is silent until a client fails to
  parse. Contract tests are the only thing that catches it.
- The `Money` converter must be written, registered, and tested **twice**. If
  either side is missed, precision is lost in exactly one direction — the worst
  case to debug.
- kotlinx requires a Gradle plugin and `@Serializable` on every transported type;
  types from libraries need explicit surrogates.
- Jackson's default behaviour is permissive. Unknown-property handling and
  `BigDecimal` number-vs-string output must be configured explicitly, not
  inherited.

### Neutral / follow-on work

- A single OpenAPI spec is the shared contract of record; both sides are
  validated against it rather than against each other.

## Verification

- Contract test (backend): a serialised amount is a JSON **string** with scale 4.
- Round-trip test (Android): `"1234.5600"` → `BigDecimal` → `"1234.5600"`, exact.
- Negative test: a JSON *number* amount in a response is rejected, not coerced.
- Malformed-response test: a truncated or wrong-typed body produces a typed error
  state, not a crash (`FM-ANDROID-005`).

> `NOT VERIFIED — no serialisation configuration, converters, or tests exist. The
> `Money` converter is specified here and implemented nowhere.`

## Interview notes

**The trap:** "We use kotlinx.serialization because it's Kotlin-native." A
library-preference answer, and the interviewer learns nothing.

**The senior answer:** Frame it as a contract problem. The JSON contract requires
monetary amounts as strings, so the real question for any serialiser is whether
it can be *forced* to honour that in both directions — and the failure mode is
silent, because a library that coerces `"1234.5600"` to a double gives you a
plausible-looking number that is subtly wrong. So a custom converter is written
and tested on both sides, including a negative test that a JSON *number* amount
is rejected rather than accepted. On library choice: Gson is disqualified for a
Kotlin client because it ignores nullability and will put `null` into a non-null
field, which surfaces as an NPE nowhere near the parse. Between kotlinx and
Moshi it is close, and I would not defend the choice hard — both use codegen,
and the decision that actually matters is the converter, not the library.
