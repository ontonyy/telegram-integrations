# Practical scenario catalog

**Checked:** 2026-11-15. Complexity: Low = configuration; Medium = integration/backend; High = custom client or sensitive multi-system workflow.

| Pilot | User / surface | Systems and data flow | Mechanism / identity | AI, cost and complexity |
|---|---|---|---|---|
| Customer support inbox | Customer chats; agent replies in helpdesk | Telegram update → ticket inbox → assignment → agent response | Bot API bot, or Business connected bot if product supports it | No AI needed. Helpdesk seat/hosting cost. Low–medium. |
| New lead capture | Prospect submits fields after disclosure and consent; manager gets a CRM reference | Bot API private chat → HubSpot contact upsert by email → configured CRM owner → manager reference alert | Bot API and HubSpot CRM API; only user-submitted fields are written, manager alert omits PII | No AI. HubSpot account/app access required. Medium. The Java sample does not create a deal or marketing subscription; see [HubSpot Java scenario](../examples/java-spring/README.md#consent-gated-sales-lead-capture-with-hubspot). |
| Appointment booking | Customer selects time and gets reminder | Bot choice or Mini App → booking API/calendar → confirmation | Bot API + Mini App for complex slots | No AI. Booking/provider fees. Medium. |
| Digital product checkout | Customer buys a digital plan/content | Bot invoice in Stars → payment event → entitlement | Bot API; bot sends receipt/status | No AI. Stars economics and support work. Medium. |
| Physical order tracker | Customer enters order and checks fulfilment | Mini App → order/stock/payment system → status bot message | Bot API + Mini App + third-party provider | No AI. Provider and hosting costs. Medium. |
| Manager daily brief | Manager receives metrics and links on phone | Scheduled CRM/ERP query → aggregation → bot digest | Bot API bot | AI optional for narrative; send aggregates only. Low. |
| Mobile approval | Manager taps approve/reject | Callback → authorization and policy service → audit → system update | Bot API inline keyboard + backend, bot identity | AI not needed; never let model authorize. Medium. |
| Internal IT request and approval | Employee submits a private request, checks status; configured manager reviews and confirms approve/reject on phone | Bot API private chat → application role directory → bounded request state → approval-chat callbacks → status/audit | Bot API; server-side employee/manager mapping; group membership/admin role is not an authorization source | No AI required. App hosting; durable storage/SSO required before production. Medium. See [Java/Spring example](../examples/java-spring/README.md#private-employee-request-and-manager-approval). |
| Channel campaign | Subscriber reads post or responds to poll | Editorial system → channel publication → opt-in CTA → CRM | Bot API publishing as bot/admin; group/channel | AI may draft; human approves. Rate/consent constraints. Low–medium. |
| AI support draft | Agent sees suggested response and edits | Helpdesk conversation + approved KB → model → draft → agent sends | Helpdesk AI or external model, channel identity unchanged | AI cost and consent/privacy review. Medium. |
| Engineering alert | On-call sees build, alert, runbook link | GitHub/Grafana → alert router → Telegram team group | Bot API contact point or automation | No AI required; redact logs. Low. |
| Incident-to-customer update | Manager sees impact and customer cases updated | Monitoring → incident service → internal alert/approval → CRM/helpdesk status | Bot API plus secured service APIs | AI optional summary; human approves external copy. Medium/high. |
| Personal chat search | User asks about accessible work discussion | TDLib authorized client → local/controlled search → results | TDLib user session | AI only after policy/consent review. High risk/complexity. |
| Custom operations panel | Manager sees chats, tasks, builds and reports together | Telegram connector + CRM + GitHub/Jira APIs → own web app | Usually Bot API webhook; TDLib only if client access truly required | AI optional. High initial build, custom UX/access control. |

For each deployment, identify what is documented by Telegram/vendor versus proposed. See the dedicated scenario documents and [capability map](capability-map.md).

## Six small pilots

1. **Client — appointment request:** Bot API menu with three service choices and a callback to a booking sheet/API. Connect a bot and booking calendar. Small code or Make/n8n; no AI.
2. **Client — order status:** Bot asks for an order reference, backend checks it, and returns a safe status. Connect bot to order API. Small Java endpoint; no AI.
3. **Manager — new lead alert:** CRM event posts lead summary and CRM link to a private manager chat. Connect CRM automation and bot. No code or a short automation; no AI.
4. **Manager — daily operations brief:** Scheduled aggregate of open/overdue tickets and incidents. Connect CRM/helpdesk and Grafana data. Small scheduled workflow; AI optional, not needed for first version.
5. **Team lead — IT request intake:** Bot collects category and short description, creates a helpdesk ticket, replies with ticket ID. Connect Bot API and helpdesk. No-code or small integration; AI optional for classification.
6. **Developer — CI/Grafana alert:** Build failure or alert posts a concise message with run link to an on-call group. Connect GitHub Actions/Grafana and bot. A few configuration steps; no AI.

These pilots deliberately start with deterministic flows. Before expanding into AI or reading business/user-account conversations, establish the relevant permissions and consent model in [AI and Telegram data](ai-and-data.md).
