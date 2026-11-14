# Product and connector catalog

**Checked:** 2026-11-14. Prices are snapshots from vendor pages; currency, billing cadence, region, taxes, promotions, AI usage, seats and limits can change. Confirm checkout terms before purchase.

| Product | Telegram connection and documented function | Price snapshot / cost model | Caveat |
|---|---|---|---|
| [Kommo Telegram](https://www.kommo.com/integrations/telegram/) | Native bot integration; incoming chats create leads, history in lead card, CRM replies, Salesbot automation | Plans page: Base $25, Advanced $35, Pro $45 per user/month; minimum six-month billing stated on page | Native path uses BotFather token, not a user account. Connector limits can differ by plan/pipeline. |
| [Kommo + Whatcrm app](https://www.kommo.com/integrations/telegram-from-whatcrm/) | Third-party marketplace listing claims QR/phone-account synchronization and multiple accounts | Listing/vendor dependent | Not Kommo native or Telegram official. Account session access is sensitive; independently audit vendor, policies, session storage and deletion. Claims not independently verified. |
| [Chatwoot](https://www.chatwoot.com/features/telegram-integration) | Bot channel shared inbox, assignment, notes, labels, automation, attachments; docs also describe Business Bot support in current version | Cloud: Free $0/agent up to 2 agents/500 conversations but Telegram not on Free; Startups $19/agent/month, Business $39/agent/month. AI credits extra (page: $20/1,000). Self-host software Community Edition is MIT; infrastructure extra. | Verify Bot vs Business channel, supported update types and 24-hour limits. Cloud data location and contract terms matter. Self-host requires operations. |
| [Intercom Telegram](https://www.intercom.com/changes/en/152537-telegram-is-now-a-supported-channel) | Telegram Bot channel in Inbox; text, photos, files, voice; Workflows, tickets, CSAT and Fin AI | Pricing page lists Essential $29/seat/month annual (promo may show $19), Advanced $85, Expert $132; Fin $0.99 per outcome | Setup docs use bot token/webhook. Do not infer Business account connection. AI and seat pricing are separate. |
| [respond.io](https://respond.io/help/channels) | Telegram bot channel in omnichannel inbox; mobile app, automation/broadcast features listed | Starter $79/mo, Growth $159/mo, Advanced $279/mo as shown (annual totals also displayed); tied to active contacts/plan | Check exact feature tier and whether expected broadcast/automation fits. Treat channel as bot unless vendor confirms otherwise. |
| [Make Telegram Bot](https://www.make.com/integrations/telegram) | Triggers/actions for bot updates, messages, media and related workflows; page indicates modules are community-maintained | Free 1,000 credits/month; checked price UI showed Core $12, Pro $21, Teams $38/mo for 10k credits (billing selector may alter terms) | Credits are usage; 15-minute minimum scheduling on Free. Validate module maintainer/support and webhook constraints. |
| [Zapier Telegram](https://zapier.com/apps/telegram/integrations) | Bot message trigger; send message/photo/poll/reaction; combine with CRM connectors | Free $0/100 tasks/month; Professional starts $19.99/mo (750 tasks; annual billing shown); Team $69/mo | Bot token; task limits and premium app policies apply. |
| [n8n Telegram node](https://docs.n8n.io/integrations/builtin/app-nodes/n8n-nodes-base.telegram/) | First-party Telegram Trigger and node actions for messages, files, chats and callback workflows | Cloud Starter €20/mo annual for 2.5k executions; Pro €50/mo for 10k; Business self-host €667/mo annual/40k executions; Community self-host no SaaS fee | Community edition has Sustainable Use License (source-available/fair-code, not OSI open source). Hosting/security costs remain. |
| [Grafana Telegram contact point](https://grafana.com/docs/grafana/latest/alerting/configure-notifications/manage-contact-points/integrations/configure-telegram/) | Sends alert notifications to chat via bot token/chat ID | Grafana OSS no license fee (AGPL-3.0); infra. Cloud usage is metered by plan/units. | Outbound notification only; 4096-character Telegram message limit documented; no approval workflow by default. |
| [Zapier MCP Telegram](https://zapier.com/mcp/telegram) | MCP tools: react, send message/photo/poll | Page states 2 Zapier tasks per MCP tool call; normal plan usage applies | Scoped actions, not chat history. AI client has only exposed tools. |
| [Chatwoot AgentBot](https://app.chatwoot.com/hc/user-guide/articles/1677497472-how-to-use-agent-bots) | Webhooks connect external logic/AI; bot state can hand conversation to agent | Chatwoot plan/hosting plus model costs | The model's access to message content must satisfy Telegram terms and customer consent. |

## Open-source and Java projects

- [TDLib](https://github.com/tdlib/td): official Telegram client library, Boost Software License; Java JNI interface, native build required. This is client-account technology, not a Bot API wrapper.
- [TelegramBots](https://github.com/rubenlagus/TelegramBots): Java Bot API library, MIT, polling/webhook and Spring Boot starters. Current release/API support should be checked against the Bot API and target Spring version.
- [Pengrad Java Telegram Bot API](https://github.com/pengrad/java-telegram-bot-api): Java HTTP client library, MIT; repository states support for current Bot API methods. Verify release compatibility before pinning.
- [Chatwoot](https://github.com/chatwoot/chatwoot): open-source helpdesk, MIT core (enterprise components have separate terms).
- [Grafana](https://github.com/grafana/grafana): AGPL-3.0, includes alerting integrations.
- [n8n](https://github.com/n8n-io/n8n): source-available Sustainable Use License, not OSI open-source.
- [Telegram Bot API server](https://github.com/tdlib/telegram-bot-api): self-hosted Bot API endpoint server, Boost license; it does not turn a bot into a user-account client.

## Other CRM candidates

HubSpot can be connected through [Zapier](https://zapier.com/apps/hubspot/integrations/telegram) or [Make](https://www.make.com/integrations/hubspotcrm/telegram); connector actions are bot-based. HubSpot advertises free CRM for up to two users and 1,000 contacts on its pricing page, with paid tiers beyond that; check current packaging. The cited integration pages establish app compatibility, not a turnkey Telegram customer inbox.

Vendor listings can be changed or localized. This catalog is a comparison starting point, not a security endorsement.

## Pricing pages used for this snapshot

[Kommo plans](https://www.kommo.com/buy/tariff/), [Chatwoot Cloud](https://www.chatwoot.com/pricing), [Chatwoot self-hosted plans](https://www.chatwoot.com/pricing/self-hosted-plans), [Intercom](https://www.intercom.com/pricing), [respond.io](https://respond.io/pricing), [Make](https://www.make.com/pricing), [Zapier](https://zapier.com/pricing), [n8n](https://n8n.io/pricing/), [Grafana Cloud](https://grafana.com/docs/grafana-cloud/platform/pricing-and-usage/), [HubSpot CRM](https://www.hubspot.com/pricing/crm).
