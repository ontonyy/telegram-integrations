# CVNoor API Telegram task seeds

**Status:** proposed; no new Jira keys assigned. The API already has internal Bot API operational alerts and release/workflow notifications at the inspected `main` commit `4badd4d0`; see [current API notes](README.md). Existing CVN-521/CVN-529/CVN-550 work may overlap. These seeds are not execution contracts. Re-read backend `docs/plans/README.md`, current `CLAUDE.md`, API/OpenAPI contract and verification matrix before creating or implementing any ticket. Backend repo rules require every implementation plan to link its Jira issue.

## Decisions and contract

| Ref | Proposed ticket outcome | Done when | Dependencies / gate |
|---|---|---|---|
| API-TG-01 | Define the separate customer/employer Telegram scope | Decision covers whether user-facing messages are needed beyond the existing internal alerts, supported recipient roles, event categories, Bot API vs Business/helpdesk and explicit non-goals | Owner/product/support decision; check existing CVN-521/CVN-529/CVN-550 scope first; no credentials |
| API-TG-02 | Define user-to-Telegram identity linkage contract | One-time, short-lived pairing flow maps only authenticated CVNoor users; unlink/revoke and duplicate/replay behavior defined | Clerk identity/session contract; confirm FE/API ownership and privacy policy |
| API-TG-03 | Define notification preferences and consent contract | Per-purpose opt-in/out, timestamps, locale, recipient state, expiry and suppression are specified | Product/legal/privacy decisions; candidate safety review |
| API-TG-04 | Define event and delivery contract | Typed events, minimal payloads, idempotency, expiry, retry classifications, status and observability defined | Align with current async job/outbox contract; verify current implementation first |

## Backend implementation candidates

| Ref | Proposed ticket outcome | Done when | Dependencies / gate |
|---|---|---|---|
| API-TG-05 | Decide whether customer notifications can reuse or need a separate provider boundary | Review existing `AlertSender`/`TelegramAlertSender` and define distinct recipient, consent, localization, expiry, retry and audit rules for customer messages; do not duplicate the existing operational sender | API-TG-01 through API-TG-04; inspect current implementation and avoid changing the internal alert contract |
| API-TG-06 | Add secure bot-linking callback/webhook path | Telegram webhook secret is verified; nonce is one-time; authenticated account link cannot be claimed by another user | Approved contract and threat model; abuse/rate limiting |
| API-TG-07 | Add notification eligibility policy | Send only when event, recipient mapping, preference, consent, locale, expiry and suppression checks pass | API-TG-02/03; explicit tests and audit semantics |
| API-TG-08 | Deliver one employer new-application alert | Generic text and signed/authenticated deep link delivered only to eligible authorized member | Frontend pilot, company authorization, consent and message disclosure reviewed |
| API-TG-09 | Add opt-in candidate status alert | Generic status notice delivered from approved state transition; no CV/contact/application detail leaks | Candidate age/safeguarding and disclosure approval; authenticated link |
| API-TG-10 | Add durable delivery only for approved user-facing notifications | Customer/employer events use the accepted work path with idempotency, bounded retries, stale-event expiry and a delivery ledger; the existing internal alert sender remains best-effort unless its owner changes that contract | Verify outbox state and operational guarantees in target environment; avoid treating a queue as consent. Learning reference: [J16 H2 outbox](../../tasks/completed-issues/J16.md); adapt it to the target PostgreSQL contract, do not treat it as production-ready code. |
| API-TG-11 | Add operator visibility for failed delivery | Approved metrics/alerts show queue age, retries and terminal failures without logging recipient content/token | Observability owner; current Cloud Monitoring/Sentry boundary |

## Security, quality and rollout

| Ref | Proposed ticket outcome | Done when | Dependencies / gate |
|---|---|---|---|
| API-TG-12 | Threat model Telegram linking and delivery | Covers spoofed webhook, replay, user enumeration, unauthorized linking, group leakage, token theft, flood and provider outage | Security review before production use |
| API-TG-13 | Add test coverage for consent and idempotency | Tests cover opted-out, missing link, duplicate event, stale link, wrong actor, provider timeout/429/5xx, permanent failure and safe logs | Fake provider/containers only; live Telegram verified separately |
| API-TG-14 | Define secret and environment setup | Token/webhook secret stored through approved secret manager flow; rotation and revocation documented | Owner/provider setup; documentation alone does not authorize storage/configuration |
| API-TG-15 | Verify an approved non-production Telegram pilot | Synthetic identity/recipient, explicit opt-in, one generic message, opt-out and suppression; or, if only validating current internal alerts, a separate authorized synthetic failure signal; evidence distinguishes source tests, HTTP success and observed receipt | All prerequisites; no production data or unsolicited messages; the current research did not send a message |

## Ordering suggestion

1. API-TG-01 through API-TG-04: policy and contracts.
2. API-TG-12 and API-TG-13: threat model and test boundaries.
3. API-TG-05 through API-TG-07: narrow secure foundation.
4. API-TG-08: employer pilot; then API-TG-09 only after youth/candidate communication decisions.
5. API-TG-10, API-TG-11, API-TG-14 and API-TG-15 before expanding volume or adding more events.

No ticket, source change, provider setup, migration, deployment or secret operation is authorized by this backlog. Pair the selected API slice with the matching [CVNoor product task](../cvnoor/tasks.md) and verify both repositories' current plans before implementation.
