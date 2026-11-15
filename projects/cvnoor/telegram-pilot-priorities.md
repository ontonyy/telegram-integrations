# CVNoor Telegram pilot priorities

**Status:** research and proposed ordering, not approved product behavior. **Checked:** 2026-11-14. The CVNoor API checkout was inspected read-only at local `main` / `origin/main` commit `4badd4d0`; the checkout had unrelated untracked work and was not modified. No Telegram message was sent, no credentials were inspected, and no live stack was connected.

## Current integration baseline

CVNoor already has Telegram uses in its backend and delivery workflows. In the inspected API source:

- `telegram-release.yml` posts one best-effort Bot API message for a stable published release into a configured group topic. It may duplicate after a rerun or uncertain timeout.
- Workflow failure handling posts the workflow/job name, short commit SHA and run link; it does not include job logs and failure to alert does not change the workflow result.
- `TelegramAlertSender` posts internal 500, repeated-503, anomaly and failed-login-threshold alerts to one configured chat/topic. It is asynchronous with a bounded in-process queue and in-memory dedup/rate limiting; messages may be dropped on saturation/provider failure. This is not the durable outbox.
- The general Telegram repository now includes [J16, a local H2 outbox teaching example](../../tasks/completed-issues/J16.md). It demonstrates durable enqueue, leased claims, bounded retry, expiry and Telegram `retry_after`; it is not integrated with CVNoor and does not verify CVNoor's PostgreSQL outbox, sender contract, or live delivery.
- Deployment source passes a Telegram token reference and target identifiers, and sets the non-production Telegram alert flag. The API docs say the Secret Manager token version and runtime accessor grant are `UNKNOWN`. The verification matrix does not establish receipt of a real alert. Workflow wiring and mocked tests are not live delivery evidence.

The paths above use a standard Bot API bot for internal operational notifications. This research did not find a candidate/employer account-linking flow, user notification preference, customer support inbox, Telegram Business connection, Mini App, inbound customer bot handler, or TDLib client in the inspected API source. Verify the current branches and work items before treating any absence as final.

Relevant existing backend scope includes CVN-521 (API failure alerts), CVN-529 (anomaly alerts) and CVN-550 (workflow/dependency alerting); confirm each ticket's current state before creating follow-up work. The API project notes in this repository distinguish the existing alert implementation from these future user-facing ideas.

## Prioritization method

Qualitative ratings reflect expected user value, access sensitivity, implementation scope and phone fit. They are research judgments, not measured user demand or product approval. “Technical maturity” means maturity of the Telegram mechanism/product pattern; “CVNoor state” means what the inspected local source establishes.

| Pilot | Main user and what they see | Value / phone fit | Technical maturity | CVNoor state / effort | Data sensitivity and gate |
|---|---|---|---|---|---|
| **1. Make current operational alerts trustworthy** | Developers and managers see a release link, failed workflow, elevated API error/dependency count or anomaly in a restricted staff topic. | High / high | High for Bot API + GitHub Actions; delivery monitoring still matters | Sender and workflow code exist; live non-production receipt is not verified. Low-to-medium effort to verify delivery, clarify drops and document routing. | Low-to-medium. Keep group restricted; avoid logs, raw URLs, tokens and candidate data. Resolve `UNKNOWN` secret version/grant through the authorized owner; test only with an approved synthetic event. |
| **2. Employer alert for a new application** | An explicitly linked company member receives a generic “new application needs review” message and opens the authenticated employer page. | High / high | High | User-facing send/link/consent path not established in the inspected source. Medium effort; reuse an existing application event only after checking current API contract and delivery guarantees. | Medium-to-high because it signals candidate activity. No name, CV, contact or application details in Telegram; company authorization and company-member opt-in must be checked server-side. |
| **3. Candidate application status and interview reminder** | A candidate gets a neutral update or reminder, then signs in to see detail or manage the appointment. | High / high | High for one-way Bot API notifications | No candidate Telegram linkage/preferences found. Medium-to-high effort; existing email digest/notification behavior may overlap. | High: users are documented as 16–29. Obtain purpose-specific opt-in, provide unlink/pause, age/safeguarding and communication-policy review; no CV or detailed status in the message. |
| **4. Human support inbox for company and candidate questions** | Customer writes to the company's Telegram profile/bot; support agents see assignment and history in a helpdesk and reply in Telegram. | High / high | High for Bot API helpdesk; Business support exists but vendor support varies | No support connector or handoff found in inspected API source. Low-to-medium if a vendor inbox is acceptable; medium-to-high if custom. | High. Keep a human handoff. Choose ordinary bot vs Telegram Business intentionally; Business means selected private chats/rights and includes data-purpose and third-party disclosure restrictions. |
| **5. Manager mobile queue / approval surface** | Authorized company manager opens an authenticated page from Telegram to review pending vacancy/application work; a simple callback may acknowledge an alert. | Medium-to-high / high | High for Bot API buttons and Mini Apps | No Telegram Mini App. Existing responsive CVNoor site/admin UI may already solve the phone need; first compare before building a duplicate. Medium-to-high effort. | High for approvals. Telegram ID or button payload is not a CVNoor role; re-check Clerk identity, company membership, current state and audit on the backend. Keep sensitive approval detail on the authenticated site. |
| **6. AI-assisted support draft** | An operator selects one authorized support ticket, receives a suggested reply, edits it and sends it manually. | Medium-to-high / medium | High as an application/model API pattern | No Telegram AI flow. Backend AI is documented as disabled by default; no AI key is provisioned in current API documentation. Medium-to-high effort plus model/provider review. | High. Do not feed Business messages or chat history to an external API by default. Disclose processing, establish authorization and scoped consent, minimize fields, retain short-term, cite approved KB sources, keep human send approval. |
| **7. Developer agent from a phone** | An individual developer asks a local Codex/Claude CLI to inspect a branch or prepare a small patch and reads progress in a private Telegram DM. | Medium / high | Early-to-high project maturity depending on bridge | No CVNoor product feature; bridge runs outside the API service. Low setup for a disposable personal clone, high security review for any shared/company host. | Very high if repo or host holds credentials/customer data. Owner-only allowlist, isolated user and checkout, normal sandbox/approvals, no production secrets, no shared group and no deploy/merge from chat. |

### Why these priorities

1. The existing staff alert path solves an immediate operational communication need, but first needs truthful delivery evidence and an explicit understanding that its in-memory sender can drop messages.
2. Employer notices offer measurable workflow value while allowing sensitive detail to stay behind the existing authenticated page.
3. Candidate notifications can help with timely status and interview updates, but user age, opt-in, and overlap with existing email/in-product notices make them a later and more sensitive pilot.
4. A helpdesk product can provide a real human queue faster than building a custom support console; test the exact account identity and Business mode before selecting one.
5. A Mini App or Telegram approval UI is justified only if phone use is materially better than the existing responsive website. Telegram launch context does not authorize a domain operation.
6. AI should first assist a human with one selected case and approved knowledge. It is not needed for routing, alerts, reminders or count thresholds.
7. Remote coding is a developer workstation experiment, never part of the customer-facing CVNoor product or production runtime.

There is no evidence in the inspected product materials that CVNoor needs a bot to read community-group history or a shared group Mini App. A native Telegram poll may fit an optional webinar/event decision; do not store candidate/application details in a group.

## Recommended order to turn into tickets

1. **Reconcile existing alert work.** Check CVN-521/CVN-529/CVN-550, verify runtime secret provisioning and destination ownership through the authorized owner, and establish safe non-production delivery evidence. No real message was sent in this research.
2. **Product decision for user-facing notifications.** Decide roles, purposes, channel choices, opt-in/unlink, locale, age/safeguarding, content minimization and suppression. Compare Telegram to existing email/in-product notifications.
3. **Employer pilot contract.** Define company member linkage, server-side authorization, generic copy, authenticated deep link, idempotency, expiry and delivery failure behavior. Use a synthetic company/user in non-production.
4. **Support product evaluation.** Demonstrate one ordinary Bot API inbox and one supported Business mode (if the company account needs to answer from its profile) with a disposable account, including a human taking over.
5. **Candidate notifications.** Only after decisions in step 2; start with one event and one locale, then add localized preference/opt-out and reminder handling.
6. **Mini App and AI.** Build only after a mobile workflow has evidence of need. Keep AI out of Telegram-origin content until the precise consent, legal and model-provider constraints are approved.

These are future ticket seeds, not tickets, approved plans, permission to create credentials, or authorization to send messages. Re-read the CVNoor API's active plans and source at implementation time.

## Sources and evidence

- CVNoor API local checkout, `main` = `origin/main` at `4badd4d0`: `app/service/.../alerts/TelegramAlertSender.java`, `app/rest/.../ApiExceptionAdvice.java`, `app/service/.../alerts/AnomalyAlertJob.java`, `app/rest/.../auth/FailedExchangeCounter.java`, `scripts/telegram-workflow-alert.sh`, `.github/workflows/deploy-nonprod.yml`, `.github/workflows/telegram-release.yml`, `docs/context/08-ci-cd-and-delivery.md`, `docs/context/10-configuration-and-flags.md`, `docs/context/09-observability-and-sentry.md`, `docs/verification-matrix.md`. The read-only checkout contained unrelated untracked files; those files and the API repository were not changed.
- Telegram [Bot FAQ](https://core.telegram.org/bots/faq), [connected Business bots](https://core.telegram.org/api/bots/connected-business-bots), [Business recipients](https://core.telegram.org/constructor/businessBotRecipients), [Business rights](https://core.telegram.org/constructor/businessBotRights), [Mini Apps](https://core.telegram.org/bots/webapps), [Bot Developer Terms](https://telegram.org/tos/bot-developers), [Content Licensing Terms](https://telegram.org/tos/content-licensing).
- General support and implementation references: [support comparison](../../02-research/support.md), [implementation landscape](../../02-research/implementation-landscape.md), [scenario catalog](../../02-research/scenario-catalog.md), and the [Java/Spring sample](../../examples/java-spring/README.md).

**Verified from source/docs:** API Bot API sender, workflow alert and release workflow paths are present at the noted commit; the API's own delivery documentation marks the runtime bot-token secret version/grant unknown and no live delivery is established. **Not run:** no CVNoor build/tests, deployment, secret query, Telegram account connection, API call, or message send. **Proposed:** all seven pilots and their order.
