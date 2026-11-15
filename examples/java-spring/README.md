# Java + Spring Boot Telegram integration starter

**Status:** local demonstration; no Telegram account is connected and no messages have been sent.

This small Java 25 / Spring Boot app contains three patterns:

1. Bot API webhook with a shared-secret header, a narrow command allowlist and dry-run replies.
2. Telegram Mini App `initData` verification on the Java server.
3. Optional Claude Messages API call for a user-submitted `/ask` prompt after `/consent` in a private bot chat.

The examples share one service so the data boundaries are visible. They do not include credentials, a live test fixture, chat history indexing, Business-chat ingestion, TDLib, file downloads, or any real send. See [remaining examples](../../tasks/1.md) for later TDLib/file exercises.

## Prerequisites

- Java 25
- Network access only if you need Gradle to download the wrapper distribution/dependencies
- No Telegram or Claude credentials for local startup and the dry-run webhook

## Run locally

```bash
./gradlew bootRun
```

Run the credential-free automated tests:

```bash
./gradlew test
```

The tests use synthetic input and an injected HTTP transport. They do not connect to Telegram or an AI provider. `JdkHttpTransport` is the production adapter; unit tests replace the transport with an in-memory stub.

From the repository root, scan tracked and non-ignored files for common Telegram and AI-provider credential formats:

```bash
bash scripts/credential-scan.sh
```

This is a focused pattern scan, not a guarantee that every possible secret format is detected. Review any new credential type before adding it to the example. Keep raw update captures outside the repository; local files under `telegram-updates/` and files ending in `.telegram-update.json` are ignored.

Dry-run webhook with synthetic test data:

```bash
curl -i -X POST http://localhost:8080/telegram/webhook \
  -H 'Content-Type: application/json' \
  -H 'X-Telegram-Bot-Api-Secret-Token: local-demo-secret' \
  -d '{"message":{"message_id":1,"chat":{"id":12345,"type":"private"},"from":{"id":12345},"text":"/start"}}'
```

The server logs that a dry-run reply would be sent (without logging its content) and returns HTTP 200. Try `/help`, `/status`, `/consent`, and `/ask Please summarize this sample issue`. Send `/start` before `/consent`. Consent exists only in memory and resets on restart. The `/ask` input is not sent anywhere unless an Anthropic key is separately configured.

Open `http://localhost:8080/` to see a Mini App shell. A normal browser does not provide Telegram-signed `initData`; the session endpoint rejects it unless a real, fresh launch payload and `TELEGRAM_BOT_TOKEN` are supplied. No credentials are provided here. The UI sends raw `initData` to the backend, never treats `initDataUnsafe` as proof of identity, and displays only a minimal identity claim.

## Optional credentials (do not use for this local demo)

All credentials are environment variables. The default `TELEGRAM_SEND_ENABLED=false` keeps sends disabled. Keep it false. Setting it true enables outbound Bot API calls when a real update is handled; this repository does not ask you to do so.

To enable the separate model call in a controlled environment, set `ANTHROPIC_API_KEY` and `ANTHROPIC_MODEL` to a model available to your account. Only a private-chat `/ask` after explicit `/consent` invokes the model. `/stop` removes consent. This is an illustration of a bounded direct-to-bot prompt; review current Telegram and provider terms before deploying it. Business message content is never routed through this code.

```bash
TELEGRAM_BOT_TOKEN='<secret from a controlled test bot>' \
TELEGRAM_WEBHOOK_SECRET='<random webhook secret>' \
TELEGRAM_SEND_ENABLED=false \
ANTHROPIC_API_KEY='<provider key>' \
ANTHROPIC_MODEL='<model name available to your account>' \
./gradlew bootRun
```

Do not commit real values, `.env` files, Telegram launch payloads, user IDs, or logs containing message bodies. TDLib session files and the `telegram-data/` directory are ignored by Git. The repository credential scan checks common Bot API, Anthropic, OpenAI and Telegram API hash formats, but it is not a complete secret detector. For a public webhook, use HTTPS, configure Telegram's webhook secret, bound request size, add idempotency/rate limits and persistent consent records, and keep API keys in a secret manager.

## Mini App validation notes

Telegram says to submit `Telegram.WebApp.initData` to the bot backend, compute the HMAC-SHA-256 using `secret_key = HMAC_SHA256(bot_token, "WebAppData")`, compare the hash in constant time, and optionally reject stale `auth_date`. The sample allows 15 minutes. A validated Telegram user ID is identity evidence, not authorization for CRM records or business actions; apply your own account/role checks.

## Limits

- Bot API only receives updates Telegram makes available to the bot. With group privacy mode enabled, this sample's `/ask` intentionally rejects group chats.
- A Mini App direct link can carry group context, but the app does not get group history or permission to send as the user.
- AI is disabled without a provider key and explicit per-chat consent. The consent store is in-memory and not production-ready.
- This repository was not configured with a live bot/provider, and no live integration was verified.
- `./gradlew test` verifies local behavior only; it does not prove Telegram delivery, provider connectivity, or account authorization.

## Documentation

- [Bot API](https://core.telegram.org/bots/api), [FAQ and group visibility](https://core.telegram.org/bots/faq)
- [Mini Apps and initData validation](https://core.telegram.org/bots/webapps)
- [Anthropic Messages API](https://platform.claude.com/docs/en/api/messages)
- [Telegram terms for bot developers](https://telegram.org/tos/bot-developers)
