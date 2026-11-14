# Telegram opportunities for CVNoor API

**Status:** project-specific notes, not an implementation plan. **Read-only API checkout check:** local `main` matched `origin/main` at `4badd4d0` on 2026-11-14. That source already contains Bot API outbound operational alerts and workflow/release notifications. This repository adds no code or credentials to the API project; no message was sent during this research.

## Backend context

The backend is Java 25 / Spring Boot 4.1.1 / Gradle multi-module. Clerk is the identity authority; Supabase PostgreSQL is the current MVP data provider; the API owns application authorization and domain operations. The backend documentation describes a PostgreSQL transactional outbox as the accepted MVP path for deferred work, but its current verification status and enabled state must be rechecked in the backend's canonical docs before implementation. A durable outbox is a delivery mechanism, not consent, notification policy, or proof of successful Telegram delivery.

Canonical sources in the CVNoor API repository:

- `CLAUDE.md` — stack, module boundaries, source-of-truth rules and verification gates.
- `docs/README.md` — current status and evidence labels.
- `docs/context/01-architecture.md`, `02-java-spring.md`, `03-identity-and-auth.md` — architecture, stateless runtime and identity.
- `docs/context/06-operations-and-runtime.md` and `docs/contracts/async-job-contract.md` — deferred work/outbox semantics.
- `docs/context/08-ci-cd-and-delivery.md`, `09-observability-and-sentry.md`, `10-configuration-and-flags.md` — delivery, observability and configuration.
- `docs/verification-matrix.md` — what has and has not been verified.

The Telegram integration must preserve the stateless API boundary, use the existing identity/authorization model, avoid storing provider secrets in source, and fit the current module/package rules. Re-read the actual target checkout and its active plans before proposing code or tickets.

General implementation references: [technical notes](../../02-research/technical-notes.md), [AI/data boundaries](../../02-research/ai-and-data.md), and [technical integrations](../../02-research/technical-integrations.md).

## Proposed integration boundary

Treat Telegram as an outbound delivery provider and optional action channel. Domain services emit typed, minimal notification intents after an authorized domain transition. A notification policy checks recipient linkage, channel preference, purpose, expiry and suppression. A provider adapter sends only approved text plus a safe deep link. Delivery outcome is recorded using the project's accepted durable work path after confirming it is ready and enabled. Retries must be idempotent and bounded; duplicate or stale event delivery must not disclose private state.

### What already exists in the inspected API source

The API source at `4badd4d0` already has a narrow internal alert sender:

- `app/service/.../alerts/TelegramAlertSender.java` calls Bot API `sendMessage` for one configured chat and optional forum topic. It uses a single daemon worker, bounded queue, in-memory deduplication and per-minute limit. Queue overflow, rate limiting or a network error drops an alert after a warning; it is not the durable outbox and has no delivery retry ledger.
- `app/rest/.../ApiExceptionAdvice.java` creates a generic 500 alert using the route template, HTTP method, error code, exception class and request id; it omits the exception message and raw URI. It also counts repeated 503 codes.
- `AnomalyAlertJob` and `FailedExchangeCounter` can send aggregate threshold alerts. Some anomaly messages include an internal company identifier; keep the destination restricted to authorized staff.
- `scripts/telegram-workflow-alert.sh` sends a workflow/job name, short commit SHA and GitHub Actions run link. It intentionally excludes error logs and does not fail the underlying workflow when Telegram is unavailable.
- `telegram-release.yml` sends one best-effort notice for a published stable release to a configured group topic. Documentation notes reruns/timeouts can duplicate it.
- `deploy-nonprod.yml` passes Telegram alert settings to the non-production Cloud Run revision. The documentation records the Secret Manager bot-token reference but says its secret version and accessor grant are `UNKNOWN`; the verification matrix does not establish a live Telegram delivery. A configured workflow or passing mock test is not proof that a real message arrived.

These are internal operational/release uses of the ordinary Bot API. The inspected source does not establish a candidate-facing Telegram account-linking flow, customer support inbox, Telegram Business connection, Mini App, or use of Telegram history. Do not create a duplicate ticket to add the basic outbound alert sender. Check the current implementation and existing CVN-521/CVN-529/CVN-550 scope before proposing changes.

For inbound bot actions, validate the webhook secret, Telegram actor/chat mapping, allowed command/action, domain authorization and current record state on every request. Never trust a callback payload as authorization. Do not make webhook requests an alternative identity authority to Clerk. Keep approval, application or employer details behind the authenticated CVNoor API/UI.

## Cross-project events worth evaluating

- Candidate application status changed → opt-in generic alert and secure link.
- Employer receives a new application or vacancy action is due → alert only to an authorized, explicitly linked company member.
- Support/admin queue threshold exceeded → internal aggregate alert with system link.
- Service incident/outbox delivery failure → on-call/manager notification without customer data or credentials.
- Invite, moderation, billing or account security events → evaluate one by one; do not send automatically without event-specific policy and privacy review.

## Telegram-specific technical notes

- Bot API is already used for internal service/workflow/release alerts. For candidate or employer DMs, a new user-linking and opt-in contract is still needed; a bot cannot start a private conversation until the user opens it.
- Telegram Business may be relevant to support staff who answer customer chats, but a normal Bot API provider adapter is not automatically a Business bot. Confirm the exact Business update/rights support in the chosen library and receiving helpdesk.
- Mini App work belongs at the UI/product boundary. Keep authoritative session and domain checks in CVNoor API; validate Telegram `initData` according to Telegram docs and do not substitute it for CVNoor authentication.
- TDLib is a user-account client library and is not recommended for backend notification delivery. It adds account session, native library and data-access risk without helping the ordinary outbound notification use case.
- No AI is necessary for event notifications. If AI is later proposed for summaries or support, define exactly which message/domain fields leave CVNoor, consent, retention and vendor processing first.

## Candidate task seeds

See [API task backlog](tasks.md). The backlog separates the existing internal alert path from future candidate/employer messaging; entries remain proposed and are not Jira tickets. The product-level backlog is [CVNoor tasks](../cvnoor/tasks.md), with a role-based comparison in [Telegram pilot priorities](../cvnoor/telegram-pilot-priorities.md).
