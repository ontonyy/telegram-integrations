# Customer support integrations

**Checked:** 2026-11-14.

## The support journey

Customer sends a message → channel connector creates or updates a conversation → helpdesk assigns an agent and stores the routed thread → agent replies in the helpdesk → connector sends the response back to Telegram. The support product, not Telegram alone, generally supplies queues, ownership, private notes, SLA automation, and human handoff.

| Option | What the customer sees | Operator view and access | Sender identity / handoff |
|---|---|---|---|
| Plain Bot API | Conversation with a bot; commands, buttons and files | Custom admin UI or logs; bot sees incoming updates available to it, not prior unrelated chats | Replies are from the bot. Human takeover must be built or connected to a helpdesk. |
| Telegram Business bot | Messages appear in a selected business user's private chat | Bot receives only configured chats and granted rights; connected account can pause or disconnect access | Can reply through the business connection and appear as the business account. Rights and reply window apply. |
| Chatwoot | Customer chats a configured bot/business bot | Shared inbox, assignment, private notes, labels, automation and conversation history for this inbox | Standard Telegram channel replies as bot. Chatwoot documents Business Bot support since v4.3; it notes a 24-hour limit and possible sender confusion when the same bot is reused in standard and Business modes. Prefer a dedicated Business bot/inbox. |
| Intercom | Customer chats the configured Telegram bot | Intercom Inbox, Workflows, tickets, CSAT and Fin AI | Documented Telegram setup uses bot token/webhook, so treat it as bot identity. Handoff is an Intercom/Fin workflow. |
| Kommo | Customer chats a linked support/sales bot | Chat history inside lead card, assignment, pipeline and Salesbot automation | Native connector is a chatbot integration, not personal-account login. Replies as bot. |

## Recommended choices

- Small team or self-hosted requirement: Chatwoot with Bot API; use its agent assignment and handoff. Self-hosting needs operations for database, Redis, storage, backups, upgrades and security.
- Sales-led support: Kommo can connect the chat to a lead and pipeline. Verify whether one bot per pipeline and the chosen plan fit the process.
- Existing Intercom operation: use its native Telegram bot channel, then configure workflow-to-ticket and human routing. Do not assume it connects a user's Telegram account or supports a Business bot unless the exact current documentation confirms it.
- Custom Java system: implement a Bot API webhook, persist a conversation/ticket ID, and build an operator surface or connect a helpdesk. For business-account replies, implement the documented Business connection and rights rather than reusing a bot token flow.

## Proposed safe flow

1. Customer starts the bot or messages the business account.
2. Connector creates an internal ticket and stores only the data needed for service.
3. Rules assign queue/agent by topic, language, or customer tier.
4. Agent replies through the same connector; use explicit sender attribution.
5. If an AI assistant drafts a response, show the draft to the agent and require a human send action until quality and authorization are established.
6. Close, export, and delete records under a documented retention policy.

## Limits and cost drivers

Bot replies cannot initiate arbitrary private conversations: the user must first contact the bot. A normal bot sees only its own conversation scope. Business bots have recipient filters, rights, and time constraints. Helpdesk cloud cost is generally seats plus AI/usage; self-hosting replaces license cost with hosting and operational work. Current product snapshots are in the [catalog](product-catalog.md).

## Evidence and references

Telegram confirms bot update and Business connection scopes: [Bot FAQ](https://core.telegram.org/bots/faq), [Business connected bots](https://core.telegram.org/api/bots/connected-business-bots), [Business rights](https://core.telegram.org/constructor/businessBotRights). Product behavior: [Chatwoot Telegram](https://www.chatwoot.com/features/telegram-integration), [Chatwoot channel setup](https://app.chatwoot.com/hc/user-guide/articles/1677492191-adding-inboxes), [AgentBot handoff](https://app.chatwoot.com/hc/user-guide/articles/1677497472-how-to-use-agent-bots), [Intercom Telegram announcement](https://www.intercom.com/changes/en/152537-telegram-is-now-a-supported-channel), [Intercom setup](https://www.intercom.com/help/es/articles/15690842-configuracion-de-telegram-en-intercom), [Kommo setup](https://support.kommo.com/docs/en/connect-telegram-to-kommo).

**Documented:** channel setup and named product features linked above. **Proposed:** joining tickets to CRM identity, routing rules, retention, and agent approval flow; verify against the organization's chosen configuration.
