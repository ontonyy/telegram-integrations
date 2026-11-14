# CVNoor Telegram opportunity brainstorm

**Date:** 2026-11-14. **Frame:** Telegram uses for a youth career-entry platform, candidates, employers, support and internal staff. Ideas are hypotheses; demand and legal approval have not been validated. Explore in the integration research repo only.

Scores are directional project-fit estimates, not market research: demand signal / buildability / defensibility, each 0–5.

| Idea | One-line value | Who / pain | Why it may fit now | Main risk | D/B/M |
|---|---|---|---|---|---|
| Application status pings | Tell applicants when a submission changes state | Candidates wait without knowing whether anything happened | Clear event can be a low-noise opt-in notification | Youth privacy, consent and notification fatigue | 4/4/1 |
| Employer application alerts | Notify a linked company member about a new applicant | Hiring teams miss time-sensitive applications | Mobile alerts can complement existing employer UI | Sensitive applicant data leaks to Telegram | 4/4/1 |
| Vacancy action reminders | Nudge employers about draft, expiry or moderation tasks | Vacancies become stale or require follow-up | Backend state can drive deterministic reminders | Incorrect/duplicate reminders erode trust | 3/4/1 |
| Candidate job digest | Deliver selected vacancy matches on chosen cadence | Candidates may not revisit the site often | Existing search/category signals could support a digest | Marketing consent, relevance, age/safeguarding, spam | 3/3/2 |
| Interview reminders | Remind candidate/employer of a scheduled meeting | Missed meetings waste time for both sides | Telegram supports simple reminder messages | No verified scheduling/calendar source yet | 3/3/1 |
| Support routing inbox | Route a Telegram support message to a human queue | Small team needs one mobile-friendly service inbox | Helpdesk products support assignment/handoff | Account/channel type, identity and record retention | 3/3/1 |
| Company member alerts | Send invites or membership updates to verified members | Team coordination can be slow | Existing company membership is a natural recipient boundary | Authentication and invite security | 2/3/1 |
| Operations queue digest | Give ops an aggregate view of overdue work | Managers need timely mobile visibility | Aggregate counts can avoid exposing personal data | Unclear source metrics and who may receive them | 3/4/1 |
| Moderation escalation | Alert staff to suspicious vacancies or reports | Safety-sensitive issues need rapid review | Existing moderation tools may benefit from routing | False positives, minors, high-stakes decisions | 3/2/1 |
| Incident-to-customer status | Convert a service outage into support/CRM status updates | Ops and customer support can become unsynchronized | Bridges technical incident and customer impact | Must not auto-send unverified claims | 3/2/2 |
| Secure Telegram account link | Let users opt in and manage notification preferences | No safe messaging channel without reliable identity link | Bot deep-link flow can pair an authenticated account | Replay/account takeover/privacy risk | 4/3/2 |
| Mini App career dashboard | Show jobs/applications inside Telegram | Users may prefer one mobile surface | Mini Apps allow rich web UI | Duplicates the responsive CVNoor app without evidence | 2/2/1 |

## Best three to validate first

### 1. Employer application alert

- **Key bet:** employers respond faster when an authorized team member receives a low-detail mobile alert.
- **Cheapest validation:** interview a few current employer users/support staff; measure current missed/late response pain before building.
- **Closest alternatives:** email/in-product notifications and CRM/helpdesk alerts. Telegram only wins if users want it and linking/consent is easy.
- **First pilot:** synthetic or invited non-production employer account, one generic alert, deep link to authenticated applicant page.

### 2. Candidate application status alert

- **Key bet:** candidates value a timely status change more than the extra channel's privacy and opt-in friction.
- **Cheapest validation:** ask candidates which events they want, frequency and channel; compare with email/in-product usage.
- **Closest alternatives:** email, account dashboard, browser push.
- **First pilot:** only after age-appropriate consent/safeguarding and message disclosure are approved; generic alert, no application details.

### 3. Operations digest and incident alert

- **Key bet:** managers act faster on actionable, aggregate mobile notices than on periodic dashboard review.
- **Cheapest validation:** create a manually curated weekly aggregate or route a synthetic test alert to a limited internal group.
- **Closest alternatives:** Grafana, email, Slack/Teams and existing dashboards.
- **First pilot:** alert plus secure runbook link, no automated production action.

## Recommendation

Start with the employer alert or internal operations digest. Keep customer/candidate chat content and AI out of the initial pilot. Build no Mini App unless user research reveals a mobile flow that the current responsive web product does not solve. Move from hypothesis to approved ticket only after product, privacy and technical owners agree.

Next artifact: [CVNoor project overview](../projects/cvnoor/README.md), [product tasks](../projects/cvnoor/tasks.md), [API overview](../projects/cvnoor-api/README.md) and [API tasks](../projects/cvnoor-api/tasks.md).
