# CVNoor Telegram task seeds

**Status:** proposed backlog seeds, not Jira tickets and not approved execution plans. IDs below are local references only. Internal Bot API operational/release alerts already exist in the inspected API source; first reconcile CVN-521/CVN-529/CVN-550 and their verification before creating overlapping work. Confirm current FE/BE contracts, owner priorities, privacy decisions and Jira status before turning a seed into a ticket.

## Discovery and safeguards

| Ref | Proposed ticket outcome | Done when | Dependencies / notes |
|---|---|---|---|
| CVN-TG-01 | Separate existing internal alerts from possible user notifications | Current release/workflow/API alert paths are listed separately from candidate/employer/support proposals; recipient, purpose, value, channel and non-goals are approved | Reconcile CVN-521/CVN-529/CVN-550 first; product owner; compare existing email/in-product UX |
| CVN-TG-02 | Define candidate communication and safeguarding policy | Age eligibility, opt-in language, frequency, quiet hours, marketing distinction, abuse/reporting and retention decisions recorded | Product/legal/safeguarding review; youth audience |
| CVN-TG-03 | Define data disclosure for Telegram messages | Approved field-level copy matrix for candidate, employer and staff; sensitive data excluded; secure-link behavior defined | Reconcile `docs/privacy-disclosure-matrix.md`; privacy/legal |
| CVN-TG-04 | Select Telegram channel and support operating model | Decision covers bot vs Business bot vs helpdesk, sender identity, operator access, retention and escalation | Support team; vendor/privacy review |

## Candidate-facing options

| Ref | Proposed ticket outcome | Done when | Dependencies / notes |
|---|---|---|---|
| CVN-TG-05 | Candidate can link or unlink Telegram from an authenticated account | One-time pairing, verified backend association, revoke/unlink, duplicate/replay protection and user-visible state defined | API contract and privacy decision; no username matching |
| CVN-TG-06 | Candidate can manage notification categories | Candidate can opt into/pause application updates, job digest and reminders separately; changes have effective timestamps | Preference schema/API ownership decision; localization |
| CVN-TG-07 | Send generic application status alert | Only after explicit preference; message reveals no sensitive status detail and links to authenticated application view | Secure event source and backend authorization; consent copy |
| CVN-TG-08 | Offer saved-search digest on Telegram | Candidate chooses content/frequency; opt-out works; no unsolicited DM; localization and abuse limits documented | Search product capability and marketing/legal review |
| CVN-TG-09 | Send interview/appointment reminder | Approved event/time source, timezone/cancellation handling and quiet hours; generic copy | Calendar/booking contract, consent, duplicate suppression |

## Employer and operations options

| Ref | Proposed ticket outcome | Done when | Dependencies / notes |
|---|---|---|---|
| CVN-TG-10 | Pilot new-application alert for opted-in employer member | Authorized member links account, gets generic alert, can disable it, and opens the existing authenticated applicant view | Company membership check, consent, safe content review; recommended pilot |
| CVN-TG-11 | Add employer vacancy action reminders | Owner receives a due/expiry/moderation alert with secure link and preference controls | Vacancy event/status contract and moderation policy |
| CVN-TG-12 | Evaluate an operations queue digest distinct from current alerts | Staff receive a scheduled aggregate workload summary only if it adds value beyond existing immediate alerts; no candidate details in group chat | Defined metrics; restricted recipient list; review existing `AnomalyAlertJob` and alert topics |
| CVN-TG-13 | Evaluate support inbox for candidate/employer service | Trial validates human assignment, sender identity, history, Business bot support, retention and escalation | Support owner; vendor security/privacy review |
| CVN-TG-14 | Add scoped internal action workflow | Staff can request a low-risk action; server rechecks role/state, writes audit event, and requires confirmation where appropriate | Do only after Telegram identity-to-staff mapping and authorization contract exist |

## Quality and rollout

| Ref | Proposed ticket outcome | Done when | Dependencies / notes |
|---|---|---|---|
| CVN-TG-15 | Define delivery guarantees separately for internal alerts and user notifications | Document current in-memory best-effort sender and its drops; define any durable retry/ledger needed for user notices without changing the alert contract implicitly | Coordinate with backend outbox/provider contract; no credential or message-body logging |
| CVN-TG-16 | Prepare non-production verification plans | First, an authorized synthetic operational alert with delivery evidence; separately, synthetic user opt-in/out, unlink, unauthorized actor, duplicate event, localization and failure checks | Must use no production data or unsolicited customer messages; record source tests, HTTP success and real receipt accurately |
| CVN-TG-17 | Decide whether Mini App solves an evidenced mobile gap | User research identifies unmet flow; prototype is compared with responsive CVNoor web UX | Do not create Mini App solely because Telegram supports it |

## Suggested order

1. Reconcile current operational alerts/CVN-521, CVN-529 and CVN-550; then CVN-TG-01 through CVN-TG-04 (user-facing decision and safeguards).
2. CVN-TG-05 plus the smallest employer pilot CVN-TG-10, only after a safe linking and disclosure design exists.
3. CVN-TG-15 and CVN-TG-16 before wider enablement.
4. Expand candidate messages, support, digests and internal actions only with their separate prerequisites.

Nothing in this backlog authorizes frontend/backend edits, provider setup, secret storage, deployment or customer contact. See the [project overview](README.md) and [API notes](../cvnoor-api/README.md).
