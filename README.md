# Telegram Integrations for Java

A practical starting point for exploring Telegram integrations in Java projects: automation, web experiences, custom clients, and carefully scoped AI workflows.

This repository is a guide and example backlog. It contains no Telegram tokens, API credentials, or account sessions.

## Choose an integration path

| Path | Best for | What it gives you | Trade-off |
|---|---|---|---|
| **Bot API** | Service alerts, commands, CI/CD notifications, support flows | A server-side bot can receive updates and send messages, files, and interactive buttons | A bot is a separate account and only sees data and actions available to that bot |
| **Telegram Mini App** | Web dashboards, forms, approval screens, lightweight product UI | A web app opens inside Telegram; a Java backend can serve its APIs and business logic | Mini Apps are part of the Bot Platform and require a bot; validate Telegram-provided launch data on the server |
| **Telegram API with TDLib** | A custom client or a user-authorized workflow across chats available to that account | A full Telegram client library; TDLib has a native Java interface via JNI and handles networking, encryption, and local storage | More complex authentication, native packaging, session protection, and API-policy obligations |
| **Direct Bot API over HTTP** | A small Java service that only sends a notification | Call Telegram's HTTPS endpoint with Java's built-in HTTP client; no bot framework required | Still uses a bot token and has Bot API permissions and visibility limits |

A user-account client is a different integration from a bot. TDLib does not grant access to chats the account cannot access, and it does not remove Telegram's API or privacy rules.

## Strong use cases

- **Build and deployment notifications:** post Maven/Gradle build, test, release, or deployment results to a team chat.
- **Operations and incident workflows:** let authorized teammates request service health, acknowledge an alert, or launch a pre-approved runbook action.
- **A web control panel inside Telegram:** use a Mini App for service dashboards, report browsing, review forms, or structured task requests, with Java handling the backend.
- **Connect development tools:** route GitHub, Jira, or monitoring events into a chosen chat, and let users open a linked web view for richer context.
- **Custom client and personal workflow tools:** use TDLib when the product genuinely needs a user-authorized Telegram client, such as a specialized multi-chat interface or local organization of accessible conversations.
- **File intake and processing:** accept user-submitted documents or logs through a bot or Mini App, then pass them to a Java service with clear retention and access controls.

For most team automation, start with the Bot API. Add a Mini App when a chat interface is too limited. Choose TDLib only when a user-account client is a real product requirement.

## AI and Telegram data

AI can be part of a wider product, but sending Telegram messages, files, or other platform data to an AI model has strict Telegram policy limits. Telegram's current terms prohibit scraping, indexing, harvesting, aggregating, or using platform data for AI/ML development, enhancement, benchmarking, or deployment, with a narrow exception requiring explicit, informed, affirmative, continuing consent from every relevant user for the specific content and chat or context. Bot Platform rules also restrict collection for AI products while allowing data users voluntarily submit directly to a bot when its intended use is clearly disclosed and they give individual, explicit, active, revocable consent.

Design AI features to avoid ingesting Telegram-origin content by default. Before building an AI workflow over that content, review the current terms and obtain the required scoped consent. A user's account authorization alone is not that consent.

## Security basics

- Keep bot tokens, `api_id` / `api_hash`, phone numbers, login codes, and TDLib session files out of source control.
- Supply credentials through a secret manager or local environment variables; commit only placeholder names and setup instructions.
- Treat a TDLib session as account-level access. Protect its encryption key and local database, restrict access, and provide a way to revoke or remove the session.
- Validate Mini App `initData` on the backend; do not trust user identity or authorization from browser fields alone.
- Request only the data and permissions the feature needs. Explain retention and deletion to users.

## Planned Java examples

The examples are tracked in [tasks/1.md](tasks/1.md). They are not implemented yet.

## Project-specific opportunities

The [CVNoor project notes](projects/README.md) map Telegram ideas to candidate, employer, support, operations and backend workflows, with local task seeds for later ticket creation. These proposals are not approved product behavior or Jira tickets.

## Telegram ecosystem research

The dated research library covers product choices, scenario designs, access boundaries, pricing snapshots, and Java implementation notes. Start at [02-research/README.md](02-research/README.md). Findings are checked **2026-11-14**; prices and platform rules can change.

## Official references

- [Telegram APIs overview](https://core.telegram.org/api)
- [Telegram Bot API](https://core.telegram.org/bots/api)
- [Telegram Mini Apps](https://core.telegram.org/bots/webapps)
- [TDLib](https://core.telegram.org/tdlib/)
- [TDLib Java API](https://core.telegram.org/tdlib/Java_API)
- [Telegram API Terms of Service](https://core.telegram.org/api/terms)
- [Telegram Terms of Service for Content Licensing](https://telegram.org/tos/content-licensing)
- [Telegram Bot Platform Developer Terms](https://telegram.org/tos/bot-developers)
