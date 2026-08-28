# ADR-002: Clean Architecture with MVVM on Android

**Status:** Accepted
**Date:** 2026-08-28
**Decided in phase:** 0
**Implemented in phase:** 2

---

## Context

The Android client must work offline, survive process death mid-sync, refresh
tokens transparently, and hold business rules (transfer validation, retry policy,
error mapping) that must be unit-testable without a device or an emulator.

The force against layering is real: Clean Architecture on a small app produces
ceremony — a use case class per action, an interface per repository, mappers
between three model representations. On a genuinely small app that is waste.

## Decision

Adopt **Clean Architecture layers with MVVM in the presentation layer**:

```
Compose UI  →  ViewModel  →  Use Case  →  Repository (interface)
                                              ↑
                             ┌────────────────┴────────────────┐
                     Room (local)                      Retrofit (remote)
```

Rules, enforced by module boundaries:

- Use cases depend on repository **interfaces**, never implementations.
- ViewModels never touch Retrofit or Room directly.
- The UI layer holds no business logic.
- Every ViewModel exposes exactly one `StateFlow<ScreenState<T>>`.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| MVVM alone, ViewModel → Repository, no use cases | Transfer validation and retry/error-mapping rules would live in ViewModels, making them untestable without Android and duplicating rules across screens that share them. |
| MVI with a reducer and single event stream | Solves the same state problem as `ScreenState` but adds a boilerplate layer. `StateFlow<ScreenState<T>>` already forces total state modelling, which was the actual goal. |
| No layering — repository calls straight from Composables | Offline-first requires a cache-coordination point. Without a repository layer there is nowhere for it to live. |

## Consequences

### Positive

- Business rules are plain Kotlin — JVM unit tests, milliseconds, no emulator.
- The remote source can be faked wholesale, which is what makes the mandated
  failure-scenario tests (401 → refresh → retry, malformed response, process
  death) practical to write.
- Swapping Retrofit or Room touches one layer.

### Negative — what this costs us

- **Ceremony.** A trivial read becomes a use case, an interface, an
  implementation, and two mappers. For simple screens this is genuine overhead
  with no payoff.
- Three model representations (network DTO, Room entity, domain model) mean
  mapping code and a class of mapping bugs that a single shared model would not
  have.
- New contributors must learn the dependency rule before they can add a screen.

### Neutral / follow-on work

- Module boundaries (ADR — see `ANDROID-ARCHITECTURE.md`) are what actually
  *enforce* the dependency rule. Without them it is only a convention.

## Verification

- ViewModel unit tests pass with fake repositories and no Android framework.
- A Gradle module-dependency check proves no `:feature:*` module depends on
  Retrofit or Room types directly.

> `NOT VERIFIED — no Android code, modules, or tests exist. The dependency rule
> is currently a documented intention with nothing enforcing it.`

## Interview notes

**The trap:** reciting the layer diagram. Everyone can draw it.

**The senior answer:** Name what the layering *buys* and admit what it costs.
Here it buys testability of exactly the scenarios that are hard to test
otherwise — token refresh races, offline reads, process death during sync —
because the remote and local sources are replaceable behind an interface. It
costs three model representations and mapping code. On a five-screen app that
trade is a loss; on a nine-feature banking client with mandated failure-mode
tests it pays.
