# ADR-015: Kotlin as the backend language

**Status:** Accepted
**Date:** 2026-08-28
**Decided in phase:** 0
**Implemented in phase:** 1

---

## Context

The master prompt specifies "Java or Kotlin (justify in ADR)" for the Spring Boot
backend. The Android client is Kotlin and non-negotiable. So the real question is
whether the backend should match it or deliberately diverge.

Two forces pull in opposite directions:

- **Toward Kotlin:** one language across client and server; domain model types
  and the error contract can be defined once in a shared shape; null-safety and
  sealed hierarchies map well onto a transaction state machine.
- **Toward Java:** the overwhelming majority of enterprise Spring Boot roles are
  Java, and this project's stated purpose is interview credibility. Writing the
  backend in Kotlin means not demonstrating daily Java-with-Spring fluency, which
  is what most backend interviews will actually probe.

That second force is genuine and should not be waved away.

## Decision

Use **Kotlin** for the backend.

The deciding argument is domain modelling, not language preference. The
transaction lifecycle is a state machine where invalid transitions must throw
domain exceptions rather than generic errors:

```
PENDING → PROCESSING → COMPLETED | FAILED
PENDING → CANCELLED
COMPLETED → REVERSED
```

Kotlin sealed interfaces plus exhaustive `when` make an unhandled transition a
**compile error**. The Java equivalent — enums plus a transition table, or the
state pattern — is a runtime check that can be forgotten. For a system whose core
claim is that invalid state transitions are impossible, moving that from runtime
to compile time is the right trade.

The same property applies to the `ScreenState`/error hierarchies and to
null-safety on nullable database columns, which in Java is annotation-based and
advisory.

**Mitigation of the Java concern:** `INTERVIEW-GUIDE.md` will cover the Java
equivalents explicitly — how the same guarantees are obtained with enums, the
state pattern, `Optional`, and records — so the Kotlin choice does not become an
excuse for not knowing Java Spring.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| **Java 21** | The strongest alternative, and better on interview-market alignment. Sealed classes and pattern matching in `switch` now give much of the same exhaustiveness. Rejected on the margin: Kotlin's null-safety is enforced in the type system rather than by annotations, and sharing one language with the Android client removes a category of DTO drift. This is a close call, honestly decided. |
| Java 17 | Same reasoning, with weaker pattern matching. |
| Kotlin on the client, Java on the server, deliberately | Demonstrates breadth, but guarantees two definitions of every DTO and error code with nothing keeping them in sync. |

## Consequences

### Positive

- One language across the stack; domain concepts expressed identically on both
  sides.
- Exhaustive `when` over sealed types makes the transaction state machine
  compiler-enforced.
- Null-safety at the type level, which matters where nullable columns meet
  domain invariants.
- `data class`, no Lombok, and less ceremony in DTOs and value objects.

### Negative — what this costs us

- **Interview-market alignment is worse.** Most Spring Boot roles are Java. This
  is the real cost, and it is paid in exactly the domain this project exists to
  serve.
- Kotlin + Spring + JPA has known friction: entity classes need `open`
  (`kotlin-allopen`), `data class` is a poor fit for JPA entities (`equals`/
  `hashCode` on a mutable identifier), and `lateinit` or nullable fields creep in
  around the ORM. These are not hypothetical — they are the first things that
  bite.
- Kotlin compilation is slower than Java's, and KSP/kapt adds more.
- The pool of Spring-specific Kotlin examples and Stack Overflow answers is
  smaller.

### Neutral / follow-on work

- `kotlin-allopen` and `kotlin-noarg` plugins are required for JPA and must be
  configured in Phase 1.
- JPA entities will be plain classes, **not** `data class`, for the reason above.
  This must be a documented convention or it will be violated.

## Verification

- The transaction state machine rejects invalid transitions, proven by a test per
  disallowed edge — and the exhaustiveness itself is proven by compilation.
- JPA entity tests confirm correct behaviour with lazy loading and proxies under
  the allopen plugin.

> `NOT VERIFIED — no backend code exists. The JPA friction described above is a
> known property of the Kotlin/Spring/Hibernate combination, not something
> observed in this project.`

## Interview notes

**The trap:** "Kotlin is more concise than Java." Conciseness is not an
architectural argument and invites a dismissive follow-up.

**The senior answer:** Tie it to a specific guarantee the system requires. The
transaction lifecycle must make invalid transitions impossible; with sealed
interfaces and exhaustive `when`, adding a state that some handler forgot breaks
the build, whereas an enum plus a transition table is a runtime check somebody
can skip. That is the argument. Then concede the cost without prompting: most
Spring roles are Java, and Kotlin with JPA has real friction — entities need
`kotlin-allopen` because Hibernate proxies require non-final classes, and
`data class` is actively wrong for entities because generated `equals`/`hashCode`
over a mutable ID breaks identity semantics in a persistence context. Knowing
*why* the friction exists is the answer; the language choice is almost
incidental.
