# AI and Telegram data access

**Checked:** 2026-11-14.

## Important policy boundary

Telegram's Content Licensing Terms prohibit scraping, indexing, harvesting, aggregation, or use of platform data for AI/ML development, enhancement, benchmarking, or deployment, except where **all relevant users** give explicit, informed, affirmative and continuing consent scoped to the specific content and chat/context. Separately, Bot Developer Terms prohibit collection beyond what is essential and always prohibit collection aimed at large datasets, ML models and AI products (including public group/channel scraping); they permit use of content users voluntarily submit directly to the bot when its purpose is clearly disclosed and each user gives individual, explicit, active and revocable consent. For Telegram Business content, the Bot Developer Terms additionally say it must not be disclosed to third parties, including third-party APIs, without the user's authorization. A consent checkbox alone does not prove a proposed bulk-history or external-model design is permitted; review the current terms and data-protection obligations before building it.

For a third-party AI provider, confirm Telegram authorization, participant consent, data-processing terms, retention, residency and deletion. Default to no AI ingestion of Telegram-origin content. Minimize/redact data and provide opt-out and human escalation.

## What each integration can see

| Integration | Data available | What it cannot be assumed to do |
|---|---|---|
| Ordinary bot | Private messages sent to the bot; group messages according to privacy/admin settings; channel updates where it is a member/admin and the update is available | Read every user's private chat, join private groups by magic, or fetch arbitrary old history |
| Business connected bot | Configured private recipients and messages within granted rights; business connection can be paused/disconnected | Access all account chats, contacts, or unrestricted past history; rights are explicit and product support varies |
| TDLib client | Data available to the signed-in user session through Telegram client API, subject to API support | Access chats the account cannot see; bypass API/terms/privacy limits |
| Vendor connector | Only the connector's documented triggers/actions and configured account scope | General chat-history access because its page merely says Telegram integration |
| MCP server | Specific callable tools exposed by server and enabled by user | Telegram-wide read access; MCP itself grants no identity or permission |

### Source-reviewed MCP example: `telegram-mcp-readonly`

The [project README](https://github.com/mshykhov/telegram-mcp-readonly) describes a Telethon user-session MCP server for account-visible chat search/history and attachment downloads. Its inspected `runtime.py` uses a strict named read-only tool allowlist by default; setup writes that mode, and the downloader is restricted to a configured directory. This is a real application-level restriction, not a Telegram account permission: the same source includes mutating tools and broader modes, and the underlying user session remains a full Telegram client session. A compromised or reconfigured service can exceed the read-only tool interface. Keep the session local, isolate its files/key, pin and review source, disable broader modes, and do not confuse access scope with permission to process content.

The same distinction applies to other MCP/TDLib readers. The [implementation landscape](implementation-landscape.md) classifies current projects and points to inspected files. No MCP project was installed, authenticated or run here.

## Claude, Codex and this environment

Neither Claude nor Codex should be assumed to have built-in Telegram access. An MCP server is a protocol endpoint: it can expose selected Telegram actions only if separately installed/configured, authenticated, and granted those scopes. Claude Code documents local stdio and remote HTTP/SSE MCP servers. OpenAI Responses API documents remote MCP servers and tool/approval controls. Codex CLI/SDK can be orchestrated for bounded or programmatic coding tasks, but that does not create a Telegram connector. For a Telegram-to-model workflow, a separate service normally receives the update, applies consent and policy checks, selects bounded content, invokes a model API/agent, and decides whether to return a draft or send a reply.

Environment check on **2026-11-14**: Codex CLI and Claude Code CLI are installed. Their listed MCP servers contained no Telegram server. Connected app tools exposed no Telegram connector, and Codex Document Control reported no connected document session. This is only a snapshot of this workspace; it does not establish general product availability. Recheck with `codex mcp list`, `claude mcp list`, and connected plugin settings before implementation.

### Interface vs source

- **Telegram as AI interface:** a user sends a prompt directly to a bot; the service may send only the voluntarily submitted prompt after disclosure and explicit, revocable consent. A basic Q&A bot does not need group history.
- **Telegram as AI source:** an agent retrieves historical messages/files from a connector or TDLib client. This is a separate, much higher-risk design. Bot API does not provide arbitrary old group history; TDLib is bounded by the signed-in account's accessible data and Telegram terms. Do not build broad indexing by default.
- **Agent draft for manager:** a manager selects an authorized ticket/message and asks for a draft; keep it unsent until a human approves it. Telegram Business content cannot be sent to a third-party API without user authorization.
- **Files:** a bot can receive a user-submitted file and download it within Bot API limits, after which a service may extract text under a declared purpose. Never execute uploaded content. Provider file APIs may have distinct storage/retention rules; map Telegram user identity to provider file IDs server-side.

**Documented:** terms and API visibility limits in the official sources below. **Proposed:** the bounded service and draft patterns above; legal, privacy, provider retention and consent review is needed for a real deployment.

## Safer AI patterns

1. **Voluntary question to bot:** clearly state that a model processes the submitted question, obtain the required active consent, send the prompt plus curated KB snippets, and allow opt-out/human support.
2. **Agent draft:** an authorized agent selects a ticket and requests a draft; model receives that ticket content and selected KB, then returns an unsent draft. Confirm that sharing this conversation with the model is authorized.
3. **Metrics summary:** send aggregated CRM/monitoring counts instead of chat text.
4. **Search over organizational knowledge:** index content the organization has rights to process; Telegram history is excluded unless a narrow compliant consent model is established.

AI may run in Chatwoot Captain/connected provider, Intercom Fin, an external API, or a self-hosted model. In all cases identify precisely which messages, files, CRM fields and knowledge documents leave the integration boundary. AI is useful for ambiguous language, retrieval, summarization and drafting; deterministic rules are cheaper and safer for fixed routing, reminders and thresholds.

**Documented:** sources below set product/platform data boundaries. **Proposed:** the four patterns are architecture suggestions and need legal/privacy review for a real deployment.

Sources: [Telegram API Terms](https://core.telegram.org/api/terms), [Content Licensing Terms](https://telegram.org/tos/content-licensing), [Bot Developer Terms](https://telegram.org/tos/bot-developers), [Bot FAQ](https://core.telegram.org/bots/faq), [TDLib](https://core.telegram.org/tdlib/), [Mini Apps](https://core.telegram.org/bots/webapps), [Claude Code MCP](https://code.claude.com/docs/en/mcp), [OpenAI remote MCP](https://developers.openai.com/api/docs/guides/tools-connectors-mcp), [Codex as a platform](https://developers.openai.com/blog/codex-as-a-platform).
