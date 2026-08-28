# ADR-001: Jetpack Compose over the Android View system

**Status:** Accepted
**Date:** 2026-08-28
**Decided in phase:** 0
**Implemented in phase:** 2

---

## Context

FinCore 360 needs an Android UI layer for roughly nine feature areas, each of
which must render four distinct states (Loading, Success, Empty, Error) rather
than a happy path alone. Two options exist: the classic View system (XML layouts,
`RecyclerView`, Fragments) or Jetpack Compose.

A genuine force pulls toward Views: the majority of *existing* enterprise Android
banking code is written in them, and interviewers frequently probe Fragment
lifecycle and `RecyclerView` internals. Choosing Compose does not remove the need
to know that material.

## Decision

Build all UI in **Jetpack Compose with Material 3**. Retain View-system knowledge
as documented material in `INTERVIEW-GUIDE.md`, but write no production XML
layouts.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| XML Views + Fragments + `RecyclerView` | State handling is imperative — rendering four states per screen means manual visibility toggling across many views, which is exactly where state bugs live. Compose makes the state → UI mapping total and explicit. |
| Hybrid — Compose in new screens, Views in existing | There are no existing screens. A hybrid buys interoperability we do not need and doubles the design-system surface. |

## Consequences

### Positive

- A screen's UI is a pure function of one `StateFlow<ScreenState<T>>`. This makes
  the "model every state" requirement structurally enforceable rather than a
  convention.
- Compose Testing can assert each state renders correctly without a device.
- The `:core:ui` design system is composable functions, not a theme-plus-styles
  XML hierarchy.

### Negative — what this costs us

- **Recomposition is a real performance hazard.** Unstable parameters and
  unnecessary lambda allocation cause frame drops that XML layouts would not
  have. This is a cost, not a footnote — it must be measured in Phase 12.
- Compose's own APIs move faster than the View system's; version upgrades carry
  more churn.
- Losing daily contact with Fragment lifecycle and `RecyclerView` means that
  knowledge has to be maintained deliberately.

### Neutral / follow-on work

- Navigation Compose is implied by this choice (see `ANDROID-ARCHITECTURE.md`).
- Baseline Profiles become worth generating once screens exist (Phase 12).

## Verification

- Compose UI tests assert all four `ScreenState` branches render, per screen.
- Frame timing on the transaction list under scroll, measured in Phase 12.

> `NOT VERIFIED — no Android code exists. No screen has been built, rendered, or
> profiled. Both bullets above are planned evidence, not observed results.`

## Interview notes

**The trap:** "Compose is better because it's declarative and modern." That is
marketing, not engineering, and a senior interviewer will push back.

**The senior answer:** The decision turns on *state cardinality*. Every FinCore
screen has four states plus authorization variants. In the View system, rendering
a state means mutating a view tree that already holds the previous state — the
bug surface is the set of transitions you forgot. In Compose the UI is derived
from state, so an unhandled state is a compile-time `when` exhaustiveness error
rather than a stale spinner in production. The cost you accept in exchange is
recomposition performance, which you now have to profile deliberately.
