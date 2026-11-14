# Telegram opportunities for CVNoor

**Status:** exploration only. **Checked against local project documentation:** 2026-11-14. Nothing here is implemented or approved product behavior.

## Project context

CVNoor is an Estonia-first career-entry platform for candidates and employers. The frontend serves vacancy discovery, applications, company/employer workflows, account management and localization (`et`, `ru`, `en`). It uses Clerk UI and a backend seam; its docs explicitly distinguish what the UI renders from what backend authorization, privacy disclosure, legal approval and production behavior establish.

Useful source documents in the CVNoor frontend repository:

- `README.md` — product and frontend boundary.
- `docs/README.md`, `docs/integrations.md` — documentation route and current integration boundaries.
- `docs/privacy-disclosure-matrix.md` — sensitive data and unresolved disclosure decisions.
- `docs/tickets/README.md` and `docs/verification-matrix.md` — current work order/evidence.

The frontend integration notes already document saved vacancy searches with a daily email digest. A Telegram digest could duplicate or compete with that channel; validate whether candidates want a second channel, and whether preferences can be shared, before proposing it.

The platform targets young people (documented age range 16–29 in project context). That makes consent, safeguarding, moderation, age eligibility and communication preferences explicit prerequisites for any messaging program. Do not infer permission to contact a candidate from account creation, application submission or a Telegram username.

## Suggested role for Telegram

Use Telegram as an optional notification and lightweight action surface that leads back to the authenticated CVNoor website. Keep canonical application, vacancy, company and account data in CVNoor systems. Send generic notifications with secure links; do not put CVs, applicant contact details, application snapshots, employer-only notes, tokens or detailed personal information in Telegram messages.

### Candidate opportunities

- Opt-in notices when an application status changes, with a link to sign in and see detail.
- Optional saved-search/job digest after the candidate chooses location, role and frequency.
- Appointment/interview reminders, subject to an agreed source of truth, cancellation flow and consent.
- Support intake that collects a category and reference number, then routes to the support team; sensitive case details stay in an authenticated channel.

### Employer and company opportunities

- Opt-in alerts to authorized company members when a new application arrives or a vacancy needs attention; show a generic alert and deep link to the existing employer UI.
- Vacancy status, expiry and moderation notifications, with actions completed in the authenticated site for the first iteration.
- Company admin digest of open vacancies and pending work.
- Support channel for employer onboarding and account issues, with identity verification before disclosing company or candidate data.

### Internal CVNoor operations

- Operations team receives aggregated queue, service-health and moderation workload summaries.
- Authorized staff receive actionable alerts for incidents or blocked jobs with a runbook link.
- Human approval stays in the existing authenticated admin interface until identity, authorization and audit requirements for Telegram actions are deliberately designed.

## Mechanism choices

- **Bot API:** likely starting point for opt-in, one-way alerts and simple commands. A bot cannot initiate a private conversation until a user starts it; it does not see all CVNoor users or their unrelated chats.
- **Mini App:** consider only if a real mobile workflow is awkward on the existing responsive web app. Avoid duplicating vacancy, candidate or employer functionality without user evidence.
- **Telegram Business/helpdesk:** candidate support or employer support may use a connected business bot/helpdesk if the support team needs to reply in Telegram. Verify which operator account, message scope, response window, data retention and product support apply.
- **TDLib:** no current CVNoor need is established. Do not use a user-account client to collect candidate or community chat history as an easier notification mechanism.

See the general [support](../../02-research/support.md), [AI and data boundaries](../../02-research/ai-and-data.md), and [technical notes](../../02-research/technical-notes.md) before turning an idea into an implementation ticket.

## Product principles and prerequisites

1. User-initiated linking: pair Telegram with a signed-in CVNoor account using a short-lived, one-time flow; do not match by username alone.
2. Explicit purpose-specific opt-in, per-category preferences, easy pause/unlink, and recorded opt-out.
3. Generic message copy; authenticate on the web before revealing application, profile, employer or account detail.
4. Rate limits, idempotency, delivery/retry status and localization for `et`, `ru`, `en`.
5. Privacy/safeguarding and age-appropriate communication review before any candidate campaign or AI processing.
6. Determine whether email, in-product notification or web push already satisfies the need before adding Telegram.

## Proposed first pilot

Start with an employer-side internal pilot: an authorized company member explicitly opts in, and receives a generic alert when a new application arrives, with a deep link to the existing authenticated applicant view. Keep candidate contact/CV data out of Telegram. Add delivery/opt-out support and test authorization before considering candidate messages.

This is a proposal, not a statement that the underlying application event, notification preference, consent, event delivery, or access check is ready. See [task seeds](tasks.md) and the [API work notes](../cvnoor-api/README.md).
