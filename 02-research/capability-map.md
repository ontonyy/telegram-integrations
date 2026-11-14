# Capability map and comparison

**Checked:** 2026-11-14. Bot API 10.4 (2026-11-09).

## What “Telegram integration” can mean

| Mechanism | Who speaks | Access boundary | Best fit | Complexity |
|---|---|---|---|---|
| Bot API | A separate bot identity | Updates and chats available to that bot; users must first contact/start it for a private conversation. Group visibility depends on privacy mode/admin status. No general account history. | Support intake, alerts, commands, forms, files, workflows | Low–medium |
| Telegram Business connected bot | A bot connected to a Business user's account | Selected private chats and granted rights; business connection can be paused/disconnected per chat. Reply right is constrained to recent incoming conversations (24 hours in the documented flow). Not a general account session. | Shared handling of opted-in business conversations | Medium; product support varies |
| Mini App | Web UI opened from a bot in Telegram | Web application gets launch context; it does not gain access to unrelated chats. Validate `initData` on server. | Catalog, booking, dashboards, structured multi-step forms | Medium; frontend + backend |
| Telegram API with TDLib | Authorized user account | Chats and content available to that signed-in account through client API. User session is powerful and sensitive; no access to inaccessible chats. | Purpose-built Telegram client or explicit user-authorized workflow | High; native library, auth, storage |
| Automation platform | Usually a bot identity | Vendor connector's triggers/actions and its credential scope; not blanket history access. | Connectors between a bot and CRM, forms, spreadsheets, alerts | Low–medium; recurring usage costs |
| Helpdesk/CRM connector | Usually a bot; some vendors may support Business bots | Only the configured channel and messages routed through it; validate Business support specifically | Agent inbox, lead records, assignment and service history | Low–medium; seat/usage pricing |
| MCP tool connector | Bot or vendor-specific integration | Only the explicitly exposed tool actions and configured credential; MCP is a tool interface, not Telegram authorization | Let an AI agent call selected messaging or workflow actions | Medium; tool permissions and audit |

## Surface comparison

| Path | One-to-one | Groups/topics/channels | Sender identity | Message/file scope | Own backend | Choose it when |
|---|---|---|---|---|---|---|
| Bot API | Yes, after user starts or contacts bot | Yes, membership and privacy/admin rights govern updates; not arbitrary history | Bot | Updates made available to bot; downloadable files subject to API limits | Usually yes (webhook or polling) | Commands, intake, alerts, buttons, polls |
| Business connected bot | Selected private chats | Recipient selector is documented for private chats; do not assume group access | Business account via connected bot, with bot attribution | Selected recipients and granted rights; business messages have strict sharing terms | Yes, unless a vendor provides hosting | Company-account inbox automation |
| Mini App | Opened through bot/chat launch paths | Direct links support shared app context; app cannot read chat messages | UI actions use app backend; bot sends bot messages | Validated launch data, optional chat context; no ambient chat history | Yes, or hosted vendor service | Structured forms, dashboards, joint selection |
| TDLib client | Account's accessible chats | Account's accessible groups/channels | Signed-in user account | Client API data available to that user session | Yes, plus native TDLib/JNI deployment | A custom client or narrow user-authorized workflow |
| Automation / vendor connector | Depends on connector | Depends on connector | Usually bot; must check product's exact mode | Only documented trigger/action scope | Vendor-hosted or own automation runtime | Connectors cover the exact flow and data boundary |

| Actor | Typical Telegram surface | Other systems | AI needed? |
|---|---|---|---|
| Customer | Bot DM, Business DM, Mini App | CRM/helpdesk/orders/payments | No for intake/status; optional for retrieval/drafts |
| Manager | Group alerts, bot DM, Mini App, helpdesk mobile app | CRM, ticketing, monitoring | No for approvals; optional for summaries/drafts |
| Employee | Internal bot DM/group/topic | ITSM, HR, knowledge base | No for forms/routing; optional for KB answers |
| Community member | Group/channel, polls, Mini App | Moderation/analytics | Optional; only scoped, disclosed use |
| Developer/on-call | CI bot, ops group/topic, alerts | GitHub, Jira, CI/CD, Grafana | No for alert routing; optional for incident summaries |

**Documented:** Telegram's recipient selector for Business bots is specifically private chats; direct-link Mini Apps can share a chat-scoped app context but cannot read chat messages or post as the user; Bot API group updates follow privacy mode and membership/rights. **Proposed:** the system flows and actor patterns above are architecture choices.

## The practical selection rule

- Need notifications, opt-in chat, or commands? Start with **Bot API**.
- Need the company account to answer selected incoming private chats? Evaluate **Telegram Business connected bots**, then confirm the receiving CRM/helpdesk actually supports Business updates and sender attribution.
- Need an order form, rich catalog, appointment selector, or dashboard? Use a **Mini App** backed by a normal web service.
- Need a custom user-account client? Use **TDLib** only with explicit account authorization, protected local session storage, and a narrow purpose.
- Need operators, assignment, and a conversation timeline? Use a **helpdesk** that supports the exact channel type. The human handoff is usually a helpdesk workflow, not a universal Bot API feature.

## Documented facts vs architecture proposals

Telegram documents the Bot API, Business bot connection and rights, Mini Apps, Stars, and TDLib separately. The data flow that joins Telegram with a CRM, ticket, AI model, or Java service is an implementation choice. A vendor's integration page should be read for its exact authentication and action list; the word “Telegram” alone does not establish account-client access.

## Primary sources

- Telegram [APIs overview](https://core.telegram.org/api), [Bot API](https://core.telegram.org/bots/api), [Bot FAQ](https://core.telegram.org/bots/faq).
- [Telegram Business](https://core.telegram.org/api/business), [connected business bots](https://core.telegram.org/api/bots/connected-business-bots), [`BusinessBotRights`](https://core.telegram.org/constructor/businessBotRights).
- [Mini Apps](https://core.telegram.org/bots/webapps), [TDLib](https://core.telegram.org/tdlib/), [TDLib Java API](https://core.telegram.org/tdlib/Java_API).
- [Business bot recipient scope](https://core.telegram.org/constructor/businessBotRecipients), [group message visibility](https://core.telegram.org/bots/faq), [forum topics](https://core.telegram.org/api/forum).
- [Bot Developer Terms](https://telegram.org/tos/bot-developers), [API Terms](https://core.telegram.org/api/terms).
