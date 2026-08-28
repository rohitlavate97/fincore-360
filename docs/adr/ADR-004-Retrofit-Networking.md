# ADR-004: Retrofit + OkHttp for Android networking

**Status:** Accepted
**Date:** 2026-08-28
**Decided in phase:** 0
**Implemented in phase:** 2 (foundation), 3 (auth interceptors)

---

## Context

The Android HTTP layer has to do considerably more than issue requests. It must:

- attach `X-Correlation-ID` to every outgoing request for cross-system tracing
- attach `Idempotency-Key` to every mutation
- detect 401, refresh the token **once**, and retry — while queueing every other
  in-flight 401 behind that single refresh rather than firing N refreshes
- retry only on network errors and 502/503/504, with exponential backoff and
  jitter, capped at three attempts, and never on a non-idempotent request
- map twelve distinct HTTP outcomes to typed `ErrorType` values

Most of that is interceptor work, not call-site work.

## Decision

Use **Retrofit** for the typed API surface over **OkHttp**, with cross-cutting
concerns implemented as OkHttp interceptors:

| Interceptor | Responsibility |
|---|---|
| `CorrelationIdInterceptor` | Generates a UUID per request, sets `X-Correlation-ID`, logs it |
| `AuthInterceptor` | Attaches the current access token |
| `TokenAuthenticator` (OkHttp `Authenticator`) | Handles 401 → single-flight refresh → retry |
| `RetryInterceptor` | Backoff with jitter, allowlisted status codes only |
| `LoggingInterceptor` | Redacting; **disabled in release builds** |

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| Ktor Client | Reasonable, and better if KMP were planned. It is not. Retrofit's `Authenticator` contract handles single-flight 401 refresh directly, which is the hardest requirement here. |
| OkHttp alone, hand-rolled API layer | Loses the typed interface and gains hand-written request building — Retrofit is a thin layer over exactly this. |
| `HttpURLConnection` / Volley | No coroutine support, no interceptor chain, effectively unmaintained for this use. |

## Consequences

### Positive

- Correlation ID and idempotency key are attached centrally, so an endpoint
  cannot forget them.
- OkHttp's `Authenticator` is invoked on 401 *after* the response, and OkHttp
  serialises it — which is what makes single-flight refresh tractable instead of
  a manual mutex around every call site.
- Interceptors are unit-testable against `MockWebServer` without a device.

### Negative — what this costs us

- **A logging interceptor is a data-exposure hazard.** Left enabled in release,
  it writes tokens and account numbers to logcat. This must be enforced by build
  type, not by remembering.
- Retrofit's error model is thin: a non-2xx response is a successful `Response`
  with `isSuccessful == false`. Mapping to typed errors is our code, and if it is
  missed the app treats a 500 as data.
- OkHttp holds a connection pool and threads that must be shared via a single
  DI-provided client, not constructed per call.

### Neutral / follow-on work

- Certificate pinning is deferred to Phase 10 (security hardening) and is
  deliberately *not* claimed before then.

## Verification

- `MockWebServer` tests covering each of the twelve documented HTTP outcomes.
- A concurrency test: N simultaneous 401s produce exactly **one** refresh call.
- A build-config assertion that the logging interceptor is absent from release.

> `NOT VERIFIED — no networking code exists. The single-flight refresh behaviour
> is a design claim; it has not been implemented or raced.`

## Interview notes

**The trap:** "We use Retrofit with a coroutine suspend function." That describes
a tutorial, not a banking client.

**The senior answer:** The interesting part is not Retrofit, it is the 401 race.
If five requests are in flight when the access token expires, five 401s come
back. A naive implementation fires five refresh calls; four of them use a
refresh token that the first call has already rotated and invalidated, so the
user is logged out mid-session. OkHttp's `Authenticator` gives a serialisation
point where one refresh runs and the rest queue on the result. That single design
choice is the difference between "token refresh works" and
`FM-ANDROID-001`/`002` in production.
