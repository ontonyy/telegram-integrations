# CVNoor Telegram task seeds

**Status:** proposed backlog seeds, not Jira tickets and not approved execution plans. IDs below are local references only. Confirm the current FE/BE contracts, owner priorities, privacy decisions and Jira status before turning a seed into a ticket.

## Discovery and safeguards

| Ref | Proposed ticket outcome | Done when | Dependencies / notes |
|---|---|---|---|
| CVN-TG-01 | Decide which CVNoor events merit Telegram delivery | Candidate/employer/ops event list, value, channel comparison, user consent and non-goals approved | Product owner; compare existing email/in-product UX |
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
| CVN-TG-12 | Add operations queue digest | Staff receive aggregate workload and delivery/incident counts, with no candidate details in group chat | Defined metrics; access-controlled recipient list |
| CVN-TG-13 | Evaluate support inbox for candidate/employer service | Trial validates human assignment, sender identity, history, Business bot support, retention and escalation | Support owner; vendor security/privacy review |
| CVN-TG-14 | Add scoped internal action workflow | Staff can request a low-risk action; server rechecks role/state, writes audit event, and requires confirmation where appropriate | Do only after Telegram identity-to-staff mapping and authorization contract exist |

## Quality and rollout

| Ref | Proposed ticket outcome | Done when | Dependencies / notes |
|---|---|---|---|
| CVN-TG-15 | Define delivery reliability and observability | Idempotency, retry, expiry, duplicate suppression, delivery failure alert, metrics and retention are specified | Coordinate with backend outbox/provider contract; test no credential or message-body logging |
| CVN-TG-16 | Prepare non-production pilot verification | Synthetic test accounts cover opt-in/out, unlink, unauthorized actor, duplicate event, localization and delivery failure | Must use no production customer messages; record actual live checks accurately |
| CVN-TG-17 | Decide whether Mini App solves an evidenced mobile gap | User research identifies unmet flow; prototype is compared with responsive CVNoor web UX | Do not create Mini App solely because Telegram supports it |

## Suggested order

1. CVN-TG-01 through CVN-TG-04 (decision and safeguards).
2. CVN-TG-05 plus the smallest employer pilot CVN-TG-10, only after a safe linking and disclosure design exists.
3. CVN-TG-15 and CVN-TG-16 before wider enablement.
4. Expand candidate messages, support, digests and internal actions only with their separate prerequisites.

Nothing in this backlog authorizes frontend/backend edits, provider setup, secret storage, deployment or customer contact. See the [project overview](README.md) and [API notes](../cvnoor-api/README.md).
