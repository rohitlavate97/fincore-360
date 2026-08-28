# TROUBLESHOOTING — FinCore 360

**Phase:** 0.

> `NOT VERIFIED — no issue has been encountered, because nothing has been built
> or run. This file is empty by design.`

---

## 1. Purpose

Known issues and their resolutions, recorded **as they are actually encountered**.

This file is deliberately not pre-populated with predicted problems. Guessed
troubleshooting steps are worse than none — they send someone down a wrong path
with false confidence during an incident.

For *anticipated* failure modes with structured investigation steps, see
[PRODUCTION-FAILURE-MODES.md](PRODUCTION-FAILURE-MODES.md). That document is
proactive analysis and is explicitly labelled as such. This one is reactive
history.

---

## 2. How to record an issue

```markdown
### [Short title]

**Date:** YYYY-MM-DD
**Phase:** N
**Symptom:** What was actually observed — error text, log line, behaviour
**Environment:** local / staging / production
**Root cause:** What was actually wrong. Not the first guess.
**Fix:** The smallest correct change
**Prevention:** Test added, or architectural change, or "none — accepted"
**Related:** FM-ID, ADR, or issue link
```

Rules:

- Record the **root cause**, not the symptom you chased first. The wrong turn is
  often more useful to the next person than the answer.
- If the fix was a workaround, say so and link the real fix.
- If no prevention was added, write "none — accepted" rather than leaving it
  blank. A blank field reads as an oversight; an explicit acceptance is a
  decision.

---

## 3. Known issues

| Issue | Impact | Workaround |
|---|---|---|
| `prompt.txt.txt` — empty stray file at repo root | Cosmetic only | Delete once confirmed unneeded |

---

## 4. Resolved issues

None. Nothing has been built.

---

## 5. First-stop diagnostics

`PLANNED — not implemented.`

Once there is a running system, this section holds the first three things to
check for any report — most usefully: find the `traceId` from the user's error
message and grep the structured logs for that `correlationId`
([OBSERVABILITY.md](OBSERVABILITY.md) §1). That is the entire reason `traceId` is
in the error contract.
