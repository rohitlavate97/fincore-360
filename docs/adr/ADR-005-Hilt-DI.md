# ADR-005: Hilt for dependency injection on Android

**Status:** Accepted
**Date:** 2026-08-28
**Decided in phase:** 0
**Implemented in phase:** 2

---

## Context

Clean Architecture (ADR-002) requires use cases to receive repository
*interfaces*, and the mandated failure-scenario tests require substituting fake
remote and local sources. Both need a construction mechanism that spans a
multi-module build (`:core:*` and `:feature:*`, see `ANDROID-ARCHITECTURE.md`)
and respects Android lifecycles — a repository scoped to the application, a
ViewModel scoped to a navigation entry.

## Decision

Use **Hilt** (Dagger with Android lifecycle integration) as the DI framework
across all modules.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| Dagger 2 directly | Same correctness guarantees, but Android component/scope wiring is hand-written. Hilt is that wiring, standardised. No benefit to redoing it. |
| Koin | Service-locator style, resolution at **runtime**. A missing binding becomes a crash on the screen that needed it — in a banking app, potentially after login on a customer's device. Hilt fails the build instead. |
| Manual constructor injection with a hand-rolled container | Viable and honest for a small app. Across sixteen modules with mixed lifecycle scopes it becomes a container nobody maintains. |

## Consequences

### Positive

- **Missing or ambiguous bindings are compile-time errors.** For a financial app
  this is the deciding property.
- `@HiltViewModel` handles `SavedStateHandle` and ViewModel scoping without
  factory boilerplate.
- Test doubles are installed with `@TestInstallIn` / `@BindValue`, which is what
  makes fake-remote failure tests practical.

### Negative — what this costs us

- **Build time.** Annotation processing (KSP) on every module adds real seconds
  to incremental builds, and this grows with the module count.
- Dagger's compile errors are notoriously opaque — a missing binding produces a
  long generated-code trace rather than a clear message. This costs onboarding
  time.
- Hilt's component hierarchy is fixed. Custom scopes are possible but awkward.

### Neutral / follow-on work

- Module boundaries determine where `@Module` definitions live; `:core:network`
  provides OkHttp/Retrofit, `:core:database` provides Room. Feature modules
  declare no infrastructure bindings.

## Verification

- The app builds with all bindings resolved (compile-time proof by definition).
- A UI test replaces the remote source with a fake via `@TestInstallIn` and drives
  an error state.

> `NOT VERIFIED — no Gradle modules, Hilt setup, or tests exist. Build-time cost
> is stated as a known property of KSP, not as a measurement of this project.`

## Interview notes

**The trap:** "Hilt reduces boilerplate." It moves boilerplate into generated
code; that is not the argument.

**The senior answer:** The argument is *when failure happens*. Koin resolves at
runtime, so a missing binding ships and crashes on a user's device. Hilt resolves
at compile time, so the same mistake breaks CI. In a domain where a crash after
login is a support call about someone's money, moving failure left is worth the
build-time cost — and the build-time cost is real and grows with modules, which
is the honest other half of the answer.
