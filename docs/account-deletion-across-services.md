# Deleting an account, end to end — the one page this repo was missing

The hardest thing to understand about this service is not in this service. Every module here is
small and layered; what takes days to reconstruct is the answer to *"what actually happens when
a user deletes their account?"*, because the answer lives in four repositories and nowhere as a
whole. This page is that whole. It is written from the code and verified by the browser suite in
`memes-ui/e2e/features/account-deletion.feature`, which drives exactly this road against the
real stack.

## The road, in order

```
gallery (memes-ui)                    security                     offboarding                 memes / comments / user-collections
      │                                  │                              │                                    │
 1. POST /account/step-up ──────────────►│                              │                                    │
      │  {action: delete-account, pwd}   │ 200 ELEVATED                 │                                    │
      │                                  │ …or 202 FACTOR_REQUIRED      │                                    │
 2. POST /account/step-up/factor ───────►│  (only when a factor is enrolled)                                  │
      │                                  │                              │                                    │
 3. POST /account/delete {purge?} ──────►│ 202                          │                                    │
      │                                  │ locks the account NOW        │                                    │
      │                                  │ (users.pending_deletion)     │                                    │
      │                                  │                              │                                    │
      │                     outbox row ──┤                              │                                    │
      │            security-events:      │                              │                                    │
      │            ACCOUNT_DELETION_REQUESTED ─────────────────────────►│                                    │
      │                                  │                              │ content-commands:                  │
      │                                  │                              │ PURGE_USER_CONTENT ───────────────►│
      │                                  │                              │                     purge per rule │
      │                                  │                              │◄── memes-events ───────────────────┤
      │                                  │                              │◄── comments-events ────────────────┤
      │                                  │                              │◄── usercollections-events ─────────┤
      │                                  │  offboarding-events:         │ (all participants confirmed)       │
      │                                  │◄─ PORTAL_CONTENT_PURGED ─────┤                                    │
      │                                  │ deletes the user for good    │                                    │
      │                                  │ mails ACCOUNT_DELETED        │                                    │
```

1. **Step-up, not a click.** The danger zone proves it is really you before anything happens: the
   password, and — when the account carries a second factor — a code on top of it. A stolen
   session cannot end an account. (`DeleteAccountDialog.tsx`; security's `StepUp` +
   `StepUpGuard`.)
2. **The lock is immediate, the deletion is not.** `POST /account/delete` answers 202 and marks
   the account `pending_deletion`. From this moment the user cannot sign in — which is *not* the
   same as being deleted, and is the single most common misreading of this flow.
3. **The wish travels as a fact.** Security writes `ACCOUNT_DELETION_REQUESTED` to its
   transactional outbox in the same transaction as the lock, and the poller publishes it to
   `security-events`. If the leaver picked a policy in the wizard, it rides along as `policy`.
4. **offboarding is the process manager.** It turns the fact into one `PURGE_USER_CONTENT`
   command on `content-commands` and then waits for every participant it was configured with
   (`OFFBOARDING_PARTICIPANTS`, `name=confirmation-topic` pairs). Participants are configuration:
   a new content service joins the saga there, without a line changing in security.
5. **Each participant purges on its own terms** and confirms on its own topic. This service's
   side is `PurgeCommandsListener` → `PurgeUserContent`, and what "purge" means per axis is the
   rule table in the README (`DELETE` / `ANONYMIZE_AUTHOR` / `KEEP_POPULAR_ANONYMIZED:n`).
6. **Only then is the account gone.** offboarding announces `PORTAL_CONTENT_PURGED` on
   `offboarding-events`; security's `OffboardingOutcomeListener` finishes the deletion for real
   and mails the goodbye. The account exists until that message arrives.

## When it goes wrong

- **A participant never confirms.** offboarding's sweeper gives up after its own timeout and
  announces `PORTAL_PURGE_FAILED`; security compensates — the account is unlocked and the user
  gets an apology mail. Nothing is half-deleted.
- **Security's own safety net** (`account-deletion.purge-timeout`, 5 min) sits deliberately
  *after* the portal's timeout, so the portal's failure announcement normally wins the race.
- **Identity-only deployments** (no portal at all) set `account-deletion.await-portal-purge=false`
  and delete immediately. Handy to know, dangerous to test with: see the trap below.

## Traps that cost an evening if nobody tells you

- **Security's `test` environment has no outbox publisher at all** —
  `@Requires(notEnv = "test")` on `OutboxPublisher`. Nothing ever reaches a broker there, so a
  deleted account merely locks and any test asserting "cannot sign in" passes while proving
  nothing about the saga. This is why the browser suite runs against the real stack.
- **`mvn package` without `clean` can ship a stale UI.** `jar:jar` skips when it thinks the
  archive is current and Spring Boot's `repackage` then wraps the old one — the container serves
  yesterday's gallery bundle while your source shows today's fix. `clean package` settles it.
- **A shared gallery is full of other people's memes.** "The first tile on the wall" is not
  yours; ask the service which meme carries your author (`GET /memes/{id}/meta`).
- **Codes are one-shot on both sides.** A scenario (or a person) that signs in twice must read
  two different mails; re-typing the first code fails with a confusing "wrong code".

## Where the pieces live

| what | where |
|------|-------|
| the wizard, the step-up dialog | `memes-ui/src/DeleteAccountDialog.tsx` |
| the lock, the outbox fact, the final deletion, the mails | `../../shared/microservice-security` (`AccountDeletionOrchestrator`, `OffboardingOutcomeListener`) |
| the process manager, participants, the sweeper | `../microservice-offboarding` |
| this service's purge | `memes-infrastructure/.../PurgeCommandsListener`, `memes-application/.../PurgeUserContent` |
| the same road, executable | `memes-ui/e2e/features/account-deletion.feature` (real stack, no stubs) |
