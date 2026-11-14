# Sales and CRM

**Checked:** 2026-11-14.

## Lead journey

Customer starts a bot or follows a Telegram Business chat link → shares a need through chat or Mini App → connector creates/updates a CRM contact and lead → CRM assigns an owner and follow-up task → manager receives a private alert → salesperson continues in CRM/helpdesk or replies through the configured Telegram channel.

| Path | Concrete option | What it does | Identity and caveat |
|---|---|---|---|
| CRM-native bot channel | Kommo Telegram | Creates a lead from incoming bot chat, shows history in lead card, supports replies, assignment and Salesbot | Bot token; not personal-account access. Product docs describe pipeline/plan constraints. |
| Automation glue | Zapier + HubSpot + Telegram Bot | A bot message trigger can start a Zap; HubSpot actions can create/update contacts or deals; Telegram actions send message/photo/poll | Bot only; each product has its own task/usage and permissions. |
| Automation glue | Make + HubSpot + Telegram Bot | HubSpot modules can trigger or update CRM records; Telegram Bot modules send messages and react to updates | The Make Telegram app page labels modules community-maintained; validate module support before production. |
| Custom Java | Bot API webhook + HubSpot/Kommo API | Exact validation, deduplication, assignment, idempotency and business rules | Requires app credentials stored outside repo and CRM API permissions. |

## Practical CRM data map

Store Telegram user/chat identifiers only as the linkage needed by the workflow. Store user-provided name/contact, consent timestamp and purpose, source campaign, request summary, CRM owner, next action, and status. Avoid treating a Telegram username as a verified email/phone or legal identity. Ask for contact details when the business needs them and explain why.

## Follow-up design

- CRM owns lead stages, assignment and due dates.
- Telegram sends a confirmation only after the user initiates or otherwise opts in.
- A team bot posts new-lead alerts to an internal group; inline actions call a secured backend endpoint that checks manager identity and current lead state.
- Sales reminders should stop after opt-out, conversion, or expiry. Do not import address books or blast scraped users.

## Complexity and cost

No-code is low complexity for a simple form-to-CRM flow; expect per-task/operation limits and debugging across vendors. CRM-native connectors reduce glue but often license per seat and may have plan-specific automation. Custom Java is medium complexity and best when deduplication, compliance, or workflow logic matters. Pricing snapshots and precise connector features are in [Product catalog](product-catalog.md).

## Confirmed vs proposed

**Documented:** Kommo's native Telegram connector accepts a bot token and puts chat/lead context in CRM. Zapier and Make expose bot triggers/actions and HubSpot CRM actions. **Proposed:** the exact lead fields, assignment rules, manager approval, and reminder schedule; these must be configured and tested in the selected account.

Sources: [Kommo Telegram](https://www.kommo.com/integrations/telegram/), [Kommo setup](https://support.kommo.com/docs/en/connect-telegram-to-kommo), [Zapier HubSpot + Telegram](https://zapier.com/apps/hubspot/integrations/telegram), [Make HubSpot + Telegram](https://www.make.com/integrations/hubspotcrm/telegram), [HubSpot CRM](https://www.hubspot.com/pricing/crm).
