# Java + Spring Boot Telegram integration starter

**Status:** local demonstration; no Telegram account is connected and no messages have been sent.

This small Java 25 / Spring Boot app contains five patterns:

1. Bot API webhook with a shared-secret header, a narrow command allowlist and dry-run replies.
2. A working Telegram Mini App request form with server-verified `initData`, owner-scoped status lookup and synthetic state.
3. A synthetic group scheduling vote using inline buttons, server-side voter allowlisting and callback de-duplication.
4. A signed Jira Cloud webhook adapter that filters issue events and sends a minimal Telegram notification.
5. Optional Claude Messages API call for a user-submitted `/ask` prompt after `/consent` in a private bot chat.

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

## Group vote example

In a group or supergroup, `/vote` posts a synthetic scheduling question with inline callback buttons. Configure `TELEGRAM_ALLOWED_VOTER_IDS` as a comma-separated allowlist of Telegram numeric user IDs in the process environment; an empty value denies every vote. The example checks the sender identity from Telegram's authenticated webhook update against this server-side allowlist for every callback. The creator can finish the vote with `/closevote <vote-id>`. Each user has one current choice and may change it; duplicate update delivery does not add another vote. The message is edited with current totals, then the creator can post final results.

This demo keeps up to 1,000 votes in process memory. Votes disappear at restart and are not shared between instances; production needs durable shared storage and authorization based on the actual group or business roles. Do not commit real user IDs. With bot privacy mode enabled, Telegram delivers relevant commands and replies to the bot rather than arbitrary group conversation; this sample does not read group history. For a simple unweighted poll, prefer Telegram's native `sendPoll`; inline buttons are useful here because the application needs per-user authorization and changeable, application-owned state. Local tests use synthetic updates and an in-process HTTP server; no real group message is sent.

## Jira Cloud webhook example

The Java endpoint `POST /jira/webhook` accepts the `jira:issue_created` and `jira:issue_updated` event shapes. Configure these events in a Jira Cloud admin webhook and set a high-entropy webhook `secret`. Jira signs the exact UTF-8 request body in `X-Hub-Signature` as `sha256=<hex>`; this sample checks that signature in constant time and rejects missing/invalid signatures. It requires `X-Atlassian-Webhook-Identifier`, which Jira documents as stable across retries, to de-duplicate delivery. Jira Cloud retries failures and can deliver events more than once. The adapter reads project, issue type, summary, status, and status changelog fields, then filters by configured project, optional issue types, and optional destination statuses. Its Telegram message includes only the issue key, escaped summary, event/status and issue link; it does not forward description, comments, or the full webhook payload.

For local use, run `./gradlew test`; the synthetic payloads, HMAC and Telegram client are all local. The sample uses one Jira tenant and retains up to 10,000 event identifiers in process memory; restart loses them, and production needs durable shared de-duplication (include tenant ID if serving several sites). It caps accepted bodies at 1 MB even though Jira documents a larger maximum delivery size. Delivery is synchronous in this small sample; production should acknowledge quickly and enqueue into a durable worker/outbox to handle bursts and prevent Telegram latency from consuming Jira's retry window. Jira requires webhook URLs to use HTTPS with a globally trusted TLS certificate; production should also follow Atlassian's published egress IP guidance. No Jira registration, public endpoint, bot token, chat ID, or live Telegram send is configured here.

Environment variables for a controlled deployment are `JIRA_WEBHOOK_SECRET`, `JIRA_PROJECT_KEY`, `JIRA_TELEGRAM_CHAT_ID`, and `JIRA_BASE_URL`; optionally set `JIRA_ISSUE_TYPES` and `JIRA_DESTINATION_STATUSES` as comma-separated filters. Telegram delivery still requires `TELEGRAM_BOT_TOKEN` and `TELEGRAM_SEND_ENABLED=true`, which is deliberately false by default. The secret is for Jira's webhook signature, separate from `TELEGRAM_WEBHOOK_SECRET`.

Jira event names, issue/changelog fields, HMAC format, HTTPS requirement and retry identifier were checked against [Atlassian's Jira Cloud webhook documentation](https://developer.atlassian.com/cloud/jira/platform/webhooks/) on 2026-11-15. That documentation states that event and field filters can also be configured at registration; this sample repeats filters server-side as a second boundary. It currently accepts only `sha256`; the Jira page says webhook signature algorithms may change, so update the validator if Atlassian changes the method.

Dry-run webhook with synthetic test data:

```bash
curl -i -X POST http://localhost:8080/telegram/webhook \
  -H 'Content-Type: application/json' \
  -H 'X-Telegram-Bot-Api-Secret-Token: local-demo-secret' \
  -d '{"message":{"message_id":1,"chat":{"id":12345,"type":"private"},"from":{"id":12345},"text":"/start"}}'
```

The `demo` profile supplies a public, non-secret local placeholder for the webhook header. The server logs only a dry-run marker without the message or destination, and returns HTTP 200. Try `/help`, `/status`, `/consent`, and `/ask Please summarize this sample issue`. Send `/start` before `/consent`. Consent exists only in memory and resets on restart. The `/ask` input is not sent anywhere unless an Anthropic key is separately configured. Without a configured webhook secret (or the explicit demo profile), the webhook returns HTTP 503.

Open `http://localhost:8080/` to inspect the Mini App form. A normal browser does not provide Telegram-signed `initData`; submit/status endpoints reject it unless a fresh launch payload and `TELEGRAM_BOT_TOKEN` are supplied. No credentials are provided here, so authenticated submission is not available in this local demo by default. The form sends raw `initData` in POST bodies, never treats `initDataUnsafe` or a browser-supplied Telegram ID as proof of identity, and displays a synthetic request status. Use HTTPS when deploying it.

The backend exposes `POST /api/miniapp/requests` to validate launch data and create a request, then `POST /api/miniapp/requests/status` to retrieve it. The server derives the owner ID from the signed `user` field; the application policy allows a verified user to create their own request and lets only that owner read it. A different verified user receives 404. The store holds at most 1,000 records in process memory, is lost on restart and is not shared between instances. Replace it with durable storage and re-check authorization at every record operation before deployment. The sample does not post results into Telegram, so it needs no chat-send rights. If launched from a group, any `chat_instance` is context only: this example does not use it to authorize access or expose group messages. Host a deployed Mini App only behind HTTPS and configure Telegram's launch URL in a controlled bot setup; no public URL or bot is configured here.

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
- The vote example handles explicit commands and callback queries only; it does not provide group membership lookup or infer roles from Telegram group permissions.
- Jira event notifications depend on an administrator registering an HTTPS webhook with a matching secret and correctly configured event scope; local tests do not verify the Jira tenant or delivery.
- A Mini App launch can include limited chat context, but this sample does not use it for authorization and receives no group history or permission to send as the user.
- AI is disabled without a provider key and explicit per-chat consent. The consent store is in-memory and not production-ready.
- This repository was not configured with a live bot/provider, and no live integration was verified.
- `./gradlew test` verifies local behavior only; signed Mini App fixtures are synthetic. It does not prove Telegram launch, Telegram delivery, provider connectivity, or account authorization.

## Documentation

- [Bot API](https://core.telegram.org/bots/api), [FAQ and group visibility](https://core.telegram.org/bots/faq)
- [Mini Apps and initData validation](https://core.telegram.org/bots/webapps)
- [Anthropic Messages API](https://platform.claude.com/docs/en/api/messages)
- [Telegram terms for bot developers](https://telegram.org/tos/bot-developers)
