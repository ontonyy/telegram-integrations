# CVNoor API Telegram task seeds

**Status:** proposed; no Jira keys assigned. These are not execution contracts. Re-read the backend `docs/plans/README.md`, current `CLAUDE.md`, API/OpenAPI contract and verification matrix in the target branch before creating a ticket. Backend repo rules require every implementation plan to link its Jira issue; do that when converting one of these seeds into a real plan.

## Decisions and contract

| Ref | Proposed ticket outcome | Done when | Dependencies / gate |
|---|---|---|---|
| API-TG-01 | Choose notification scope and provider mode | Decision covers standard bot vs Business connector, recipients, event categories, user initiation/linking, provider and non-goals | Owner/product/support decision; no credentials |
| API-TG-02 | Define user-to-Telegram identity linkage contract | One-time, short-lived pairing flow maps only authenticated CVNoor users; unlink/revoke and duplicate/replay behavior defined | Clerk identity/session contract; confirm FE/API ownership and privacy policy |
| API-TG-03 | Define notification preferences and consent contract | Per-purpose opt-in/out, timestamps, locale, recipient state, expiry and suppression are specified | Product/legal/privacy decisions; candidate safety review |
| API-TG-04 | Define event and delivery contract | Typed events, minimal payloads, idempotency, expiry, retry classifications, status and observability defined | Align with current async job/outbox contract; verify current implementation first |

## Backend implementation candidates

| Ref | Proposed ticket outcome | Done when | Dependencies / gate |
|---|---|---|---|
| API-TG-05 | Add notification provider port and Bot API adapter | Outbound message uses configured secret, bounded timeouts, API error mapping and no token/body logging | Approved provider choice; dependency/library compatibility reviewed; no module unless ADR justifies |
| API-TG-06 | Add secure bot-linking callback/webhook path | Telegram webhook secret is verified; nonce is one-time; authenticated account link cannot be claimed by another user | Approved contract and threat model; abuse/rate limiting |
| API-TG-07 | Add notification eligibility policy | Send only when event, recipient mapping, preference, consent, locale, expiry and suppression checks pass | API-TG-02/03; explicit tests and audit semantics |
| API-TG-08 | Deliver one employer new-application alert | Generic text and signed/authenticated deep link delivered only to eligible authorized member | Frontend pilot, company authorization, consent and message disclosure reviewed |
| API-TG-09 | Add opt-in candidate status alert | Generic status notice delivered from approved state transition; no CV/contact/application detail leaks | Candidate age/safeguarding and disclosure approval; authenticated link |
| API-TG-10 | Route delivery through durable work path | Telegram send is asynchronous, idempotent, bounded, retryable and reconciled after uncertain outcomes | Verify outbox is enabled/verified in target environment; handler operational evidence |
| API-TG-11 | Add operator visibility for failed delivery | Approved metrics/alerts show queue age, retries and terminal failures without logging recipient content/token | Observability owner; current Cloud Monitoring/Sentry boundary |

## Security, quality and rollout

| Ref | Proposed ticket outcome | Done when | Dependencies / gate |
|---|---|---|---|
| API-TG-12 | Threat model Telegram linking and delivery | Covers spoofed webhook, replay, user enumeration, unauthorized linking, group leakage, token theft, flood and provider outage | Security review before production use |
| API-TG-13 | Add test coverage for consent and idempotency | Tests cover opted-out, missing link, duplicate event, stale link, wrong actor, provider timeout/429/5xx, permanent failure and safe logs | Fake provider/containers only; live Telegram verified separately |
| API-TG-14 | Define secret and environment setup | Token/webhook secret stored through approved secret manager flow; rotation and revocation documented | Owner/provider setup; documentation alone does not authorize storage/configuration |
| API-TG-15 | Verify non-production end-to-end pilot | Synthetic account links, opts in, receives one generic notification, opts out; evidence labels distinguish observed vs not run | All prerequisites; no production data or unsolicited messages |

## Ordering suggestion

1. API-TG-01 through API-TG-04: policy and contracts.
2. API-TG-12 and API-TG-13: threat model and test boundaries.
3. API-TG-05 through API-TG-07: narrow secure foundation.
4. API-TG-08: employer pilot; then API-TG-09 only after youth/candidate communication decisions.
5. API-TG-10, API-TG-11, API-TG-14 and API-TG-15 before expanding volume or adding more events.

No ticket, source change, provider setup, migration, deployment or secret operation is authorized by this backlog. Pair the selected API slice with the matching [CVNoor product task](../cvnoor/tasks.md) and verify both repositories' current plans before implementation.
