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
./gradlew bootRun --args='--spring.profiles.active=demo'
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

The direct sender uses Java's `HttpClient`. Tests point its API base URL at an in-process HTTP server; the default URL remains Telegram's official endpoint. The sample does not retry sends automatically: for HTTP 429, follow Bot API `retry_after` guidance with bounded backoff in a production queue, because blindly repeating a request can duplicate a message. A Bot API bot is a separate account and cannot search arbitrary account history; a user must open the bot or add it to an authorized chat before the bot can message there.

## GitHub Actions notification example

The repository workflow at `.github/workflows/java-sample-ci.yml` runs the Java sample tests on pull requests, but deliberately skips Telegram notification steps for every pull request, including same-repository branches. On pushes to `master` and manual runs against `master`, it reports the final workflow status and a link to that run. With no configuration it stays in dry-run mode. To opt in later, configure `TELEGRAM_BOT_TOKEN` as a GitHub Actions secret and `TELEGRAM_CHAT_ID` as a repository variable; users must first open the bot or add it to an authorized group. These values are not present in this repository. Fork pull requests never receive the secret. The workflow has only `contents: read` permissions.

The notification is HTML-formatted with dynamic values escaped and includes no logs. You can inspect the formatter without credentials:

```bash
CI_JOB_STATUS=failure GITHUB_REPOSITORY=demo/project GITHUB_REF_NAME='feature/<test>' GITHUB_SHA=0123456789abcdef GITHUB_SERVER_URL=https://github.com GITHUB_RUN_ID=123 TELEGRAM_SEND_ENABLED=false ./gradlew -q notifyTelegram
```

This prints a dry-run marker and omits the message body. The CI notifier's formatter and send mode are covered by credential-free tests; no actual chat is contacted by local tests. Do not use `pull_request_target` to run untrusted code with Telegram secrets.

Dry-run webhook with synthetic test data:

```bash
curl -i -X POST http://localhost:8080/telegram/webhook \
  -H 'Content-Type: application/json' \
  -H 'X-Telegram-Bot-Api-Secret-Token: local-demo-secret' \
  -d '{"message":{"message_id":1,"chat":{"id":12345,"type":"private"},"from":{"id":12345},"text":"/start"}}'
```

The `demo` profile supplies a public, non-secret local placeholder for the webhook header. The server logs only a dry-run marker without the message or destination, and returns HTTP 200. Try `/help`, `/status`, `/consent`, and `/ask Please summarize this sample issue`. Send `/start` before `/consent`. Consent exists only in memory and resets on restart. The `/ask` input is not sent anywhere unless an Anthropic key is separately configured. Without a configured webhook secret (or the explicit demo profile), the webhook returns HTTP 503.

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

Do not commit real values, `.env` files, Telegram launch payloads, user IDs, or logs containing message bodies. TDLib session files and the `telegram-data/` directory are ignored by Git. The repository credential scan checks common Bot API, Anthropic, OpenAI and Telegram API hash formats, but it is not a complete secret detector. The sample's bounded in-memory update registry retains at most 10,000 IDs, deduplicates only within one process and loses entries on restart; it cannot guarantee exactly-once external delivery if a provider accepts a request but the response is lost. Production should provide durable shared idempotency and an outbox for external side effects. For a public webhook, use HTTPS, configure Telegram's webhook secret, bound request size, add rate limits and persistent consent records, and keep API keys in a secret manager.

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
