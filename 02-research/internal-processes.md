# Internal processes

**Checked:** 2026-11-14.

## Useful patterns

| Process | Employee experience | Systems and flow | Suggested mechanism |
|---|---|---|---|
| IT request | `/help` or short intake form; gets ticket number/status | Telegram → ticket API/helpdesk → assignment → status notification | Bot for simple categories; Mini App for structured device/access forms |
| Approval | Sees request summary and approve/reject options on phone | Bot callback → policy service → audit record → HR/finance/CRM action | Bot API + secured backend; two-person approval where policy requires |
| Knowledge lookup | Ask a command or open searchable internal portal | Bot/Mini App → access-controlled knowledge API → answer with source link | Search first; optional AI retrieval for ambiguous questions |
| Handover | Team receives owner, context, due time and next action | Ticket/task event → routing rules → team channel | Bot notification plus Jira/ServiceNow/CRM as system of record |

## Data and security boundaries

Telegram group membership is not a complete employee authorization system. Map Telegram user IDs to company identity, revoke access on offboarding, and recheck authorization on every action. Do not post secrets, health/HR content, or full customer records in broad groups. Keep sensitive forms in an authenticated web app or Mini App backed by an authorization service.

## Role of AI

AI can categorize a ticket, retrieve a relevant internal procedure, summarize a handover, or draft an answer. It should receive only role-authorized data and return references; an operator confirms consequential actions. Basic form routing, reminders, and approval rules need no AI.

## Costs and complexity

No-code is suitable for low-risk intake and notifications. Approvals that change money, access, HR records, or production require custom authorization/audit logic even if a workflow platform carries the trigger. Knowledge search can start with a curated FAQ; AI adds model, hosting, indexing, and review costs.

**Documented:** Bot API updates and Mini App launch mechanisms. **Proposed:** integration with an employer's ticketing, identity, policy and audit services.

Sources: [Bot API](https://core.telegram.org/bots/api), [Mini Apps](https://core.telegram.org/bots/webapps), [Telegram Bot Developer Terms](https://telegram.org/tos/bot-developers).
