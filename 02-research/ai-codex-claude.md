# Telegram with Claude, Codex and other AI systems

**Checked:** 2026-11-14.

## Two separate designs

### A. Telegram is the AI interface

`user submits a prompt to a bot → webhook service validates the update and consent → service calls model API → response is returned as a bot message or kept as a manager draft`

For a simple help assistant, retrieve only approved knowledge-base documents. User-submitted prompt content may be processed only with clear disclosure and the required active, revocable consent. For Business conversations, do not send messages/files to a third-party model API without the user's authorization. Prefer an unsent draft for a human operator.

### B. Telegram is an AI data source

`AI client → explicitly configured connector/service → permitted Telegram content → source-linked response`

This requires a real connector, authentication and declared scope. Bot API is not a general history endpoint. TDLib acts as a Telegram client under an authorized account; it does not expose inaccessible chats or exempt collection from Telegram terms. MCP is only a protocol: the server's tools and configured credentials define access.

## Claude and Codex options

| Option | Confirmed capability | What it does not establish |
|---|---|---|
| Claude Messages API | An application can send text/image inputs to Claude from its backend | No Telegram connectivity, update handling, or participant consent by itself |
| Claude Code MCP | Claude Code can use separately configured local stdio and remote HTTP/SSE MCP servers | No default Telegram MCP, nor broad scope unless that server explicitly exposes it |
| Codex CLI / SDK / app-server | Bounded non-interactive CLI and programmatic coding-agent workflows are available | Not a hosted “Codex API” for Telegram; no default Telegram connector |
| OpenAI Responses API remote MCP | Can call a configured remote MCP server with tool controls/approvals | Does not make Telegram data available until a suitable server is installed and authorized |
| Own integration service | Can receive Bot API updates, apply rules, call an AI API and return drafts/replies | Needs custom implementation, privacy/terms review, monitoring and credentials |

On **2026-11-14**, Codex CLI and Claude Code CLI were installed in this workspace. Their MCP listings showed no Telegram server. Connected app tools exposed no Telegram connector and Codex Document Control had no connected document session. This is a snapshot of this environment, not a statement about every plan or third-party product. Recheck before choosing a connector.

## File and source handling

A bot can receive a file that the user sends to it, then the service can download it within Bot API constraints. A bounded pipeline can extract text, scan/validate file type and send selected content to a model only when the use is authorized. Never run uploaded code or shell commands. If using a provider's file API, map provider file IDs to the submitting user on your server and review provider retention/deletion controls.

To return citations, store source identifiers and offsets in your own permitted knowledge base and have the model return those identifiers; do not claim Telegram message permalinks or historical access unless the connector actually provides them.

## Telegram policy boundary

Telegram Content Licensing Terms prohibit scraping/indexing/aggregation and AI/ML development, enhancement, benchmarking or deployment except with explicit, informed, affirmative, continuing consent from all relevant users, scoped to the content and chat/context. Bot Developer Terms prohibit collection beyond service necessity and any collection aimed at building datasets, ML models or AI products; they allow data voluntarily submitted directly to a bot only with clear purpose disclosure and individual, explicit, active, revocable consent. For Business data, third-party API disclosure requires the user's authorization. These rules mean that broad archive indexing or sending Business chats to Claude/OpenAI must not be inferred as allowed just because an account connected or an operator has access.

## Sources

- [Telegram Content Licensing Terms](https://telegram.org/tos/content-licensing)
- [Telegram Bot Developer Terms](https://telegram.org/tos/bot-developers)
- [Telegram API Terms](https://core.telegram.org/api/terms)
- [Claude Code MCP documentation](https://code.claude.com/docs/en/mcp)
- [Anthropic Messages API](https://platform.claude.com/docs/en/api/messages)
- [OpenAI MCP tools and connectors](https://developers.openai.com/api/docs/guides/tools-connectors-mcp)
- [Codex as a platform](https://developers.openai.com/blog/codex-as-a-platform)
- [Bot API FAQ](https://core.telegram.org/bots/faq)
- [TDLib](https://core.telegram.org/tdlib/)

**Documented:** tool interfaces and Telegram policy statements in these sources. **Proposed:** the bounded service, citations and human-draft architecture.
