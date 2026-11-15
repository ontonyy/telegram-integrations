# Java + Spring Boot Telegram integration starter

**Status:** local demonstration; no Telegram account is connected and no messages have been sent.

This Java 25 / Spring Boot app contains eight patterns:

1. Bot API webhook with a shared-secret header, a narrow command allowlist and dry-run replies.
2. A working Telegram Mini App request form with server-verified `initData`, owner-scoped status lookup and synthetic state.
3. A synthetic group scheduling vote using inline buttons, server-side voter allowlisting and callback de-duplication.
4. A signed Jira Cloud webhook adapter that filters issue events and sends a minimal Telegram notification.
5. A signed Grafana Alerting webhook with an allowlisted, application-only acknowledgement button.
6. A consent-based Bot API text-file intake that downloads to a private temporary directory and deletes immediately.
7. Optional Claude Messages API flow for a directly submitted private-chat question, with disclosure, feature consent, and a separate confirmation for each exact prompt.
8. Mock-backed Telegram Business support intake: connection rights, new/edit/delete updates, a minimal synthetic ticket, an allowlisted operator takeover, and a reply path that checks connection status, `can_reply`, and the 24-hour window before including `business_connection_id`.

The examples share one service so the data boundaries are visible. They do not include credentials, a live Telegram fixture, chat history indexing, TDLib, or any real send. Text-file intake is included in this Spring sample; the separate [optional TDLib Java example](../tdlib-java/README.md) is not part of Spring startup and requires an explicit native/JNI setup. See [the task overview](../../tasks/1.md) for scope and verification boundaries.

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

## Grafana Alerting webhook and acknowledgement

Grafana already provides a native Telegram contact point for send-only alert notifications. Use that when direct routing is enough. This Java adapter is for a team that needs custom filtering, a backend-owned record and an explicit acknowledgement action. Grafana's webhook contact point posts a JSON envelope with `status`, `alerts[]`, and alert fields such as `labels`, `startsAt`, `generatorURL` and `fingerprint`. The sample emits one concise message per alert (at most 10 per payload), using `alertname`, `severity`, selected `team`/`service`/`environment` labels, firing/resolved state and a validated source link. It omits annotations, other labels, values and the full payload.

The `/grafana/webhook` endpoint requires a secret plus HMAC-SHA256 signature and timestamp. Configure Grafana's HMAC signature with header `X-Grafana-Alerting-Signature`, timestamp header `X-Grafana-Timestamp`, and the same `GRAFANA_WEBHOOK_SECRET`; the signature must match `HMAC-SHA256(secret, timestamp + ":" + rawBody)` and timestamps older/newer than five minutes are rejected. The five-minute window is the sample's policy. Requests are capped at 1 MB.

The Telegram message includes an **Acknowledge** inline button. Add `callback_query` to the bot webhook's `allowed_updates`; set `GRAFANA_ACK_USER_IDS` to a comma-separated list of allowed Telegram user IDs. Empty means nobody is authorized. A successful action records the allowlisted Telegram ID and UTC time in bounded in-memory application state and edits the notification to show that acknowledgement. Repeated taps are idempotent. This does not acknowledge, resolve, or silence anything in Grafana and makes no Grafana API call. The action applies only to the configured Telegram chat. The bot needs to be able to send messages to that chat; the sample does not read chat history.

The acknowledgement store is capped at 1,000 alert instances and clears on restart; use durable shared storage and an auditable retention policy before production. The adapter does not have a stable Grafana webhook delivery ID, so Grafana's configured repeat notifications may produce another alert message. Limit alert count and notification frequency in Grafana to fit the operational channel. Telegram sends remain disabled unless explicitly enabled in the process environment.

Local verification uses synthetic firing/resolved payloads, generated signatures and a fake Telegram client. No Grafana instance, public contact point, Telegram account or real message was used. A live setup would require an HTTPS Java endpoint reachable from Grafana, a Grafana contact point with HMAC/timestamp configured, `GRAFANA_WEBHOOK_SECRET`, `GRAFANA_TELEGRAM_CHAT_ID`, `GRAFANA_ACK_USER_IDS`, `TELEGRAM_BOT_TOKEN`, and an explicit decision to set `TELEGRAM_SEND_ENABLED=true`.

Payload shape, available webhook authentication, HMAC/timestamp calculation and the native Telegram contact point were checked against [Grafana's webhook contact point documentation](https://grafana.com/docs/grafana/latest/alerting/configure-notifications/manage-contact-points/integrations/webhook-notifier/), [Grafana's Telegram contact point documentation](https://grafana.com/docs/grafana/latest/alerting/configure-notifications/manage-contact-points/integrations/configure-telegram/) and [contact point overview](https://grafana.com/docs/grafana/latest/alerting/fundamentals/notifications/contact-points/) on 2026-11-15.

## Private, consent-based text-file intake

In a private chat, `/file-help` explains the purpose and limits; the user must then send `/file-consent` before a document is processed. `/file-stop` revokes consent. Consent is in-memory and resets on restart. Documents sent in groups are ignored. The sample accepts `.txt`, `.log`, `.java`, `.json`, and `.csv` only when the MIME type matches the extension allowlist and the file decodes as UTF-8 text without NUL bytes. Names containing `/`, `\`, or longer than 128 characters are rejected; user filenames are never used as disk paths. The sample counts lines and reports whether a simple stack-trace pattern appears. It never renders, executes, extracts, or sends the content to AI.

Default `TELEGRAM_FILE_MAX_BYTES` is 1 MiB, and the configured value cannot exceed the current cloud Bot API download limit of 20 MB. The size declared in the incoming document is checked before the API call, `getFile` metadata is checked again, and the streaming download enforces the limit while writing. At most two downloads run concurrently. The implementation uses Telegram's documented `getFile` call followed by `https://api.telegram.org/file/bot<token>/<file_path>`; its file path is treated as untrusted and validated. Files are written using owner-only directory/file permissions under `TELEGRAM_FILE_TEMP_DIR` (default: `${java.io.tmpdir}/telegram-private-uploads`) and deleted in `finally` after success, rejection, download failure, or interruption. If the filesystem refuses cleanup, the app emits a generic cleanup error without the file path or contents; an operator must remove residual temporary data. The text exists transiently in process memory while being counted.

Downloading is disabled by default. To enable the inbound file path in a controlled environment, supply `TELEGRAM_BOT_TOKEN`, set `TELEGRAM_FILE_DOWNLOAD_ENABLED=true`, configure a private temporary directory with enough space, and configure the existing webhook secret. This does not enable outbound Telegram sending; `TELEGRAM_SEND_ENABLED` remains false unless separately changed. The user must first open the bot in private chat. This sample and its tests include no customer files, Telegram IDs, bot credentials, or file IDs. Tests use synthetic text, a fake file gateway, and an in-process HTTP server to verify the Bot API `getFile` then download sequence.

The Bot API file flow and current size/link details were checked in [getFile and File documentation](https://core.telegram.org/bots/api#getfile) on 2026-11-15. The disclosure, direct-submission consent, revocation and data minimization follow the [Bot Developer Terms](https://telegram.org/tos/bot-developers#4-privacy); production services still need an accessible privacy policy and an applicable retention policy.

Dry-run webhook with synthetic test data:

```bash
curl -i -X POST http://localhost:8080/telegram/webhook \
  -H 'Content-Type: application/json' \
  -H 'X-Telegram-Bot-Api-Secret-Token: local-demo-secret' \
  -d '{"message":{"message_id":1,"chat":{"id":12345,"type":"private"},"from":{"id":12345},"text":"/start"}}'
```

The `demo` profile supplies a public, non-secret local placeholder for the webhook header. The server logs only a dry-run marker without the message or destination, and returns HTTP 200. Try `/ai-help`, `/consent`, `/ask Please summarize this sample issue`, then `/ai-confirm`. The exact prompt stays only in volatile application memory until confirmation, cancellation, revocation, or restart; the provider is not called before `/ai-confirm`. Use `/ai-cancel` to discard a staged prompt and `/stop` to revoke AI consent and clear it. The example never adds chat history, files, Business messages, or other users' messages to the request. Without configured Anthropic credentials, confirmation still makes no network call. Without a configured webhook secret (or the explicit demo profile), the webhook returns HTTP 503.

Open `http://localhost:8080/` to inspect the Mini App form. A normal browser does not provide Telegram-signed `initData`; submit/status endpoints reject it unless a fresh launch payload and `TELEGRAM_BOT_TOKEN` are supplied. No credentials are provided here, so authenticated submission is not available in this local demo by default. The form sends raw `initData` in POST bodies, never treats `initDataUnsafe` or a browser-supplied Telegram ID as proof of identity, and displays a synthetic request status. Use HTTPS when deploying it.

The backend exposes `POST /api/miniapp/requests` to validate launch data and create a request, then `POST /api/miniapp/requests/status` to retrieve it. The server derives the owner ID from the signed `user` field; the application policy allows a verified user to create their own request and lets only that owner read it. A different verified user receives 404. The store holds at most 1,000 records in process memory, is lost on restart and is not shared between instances. Replace it with durable storage and re-check authorization at every record operation before deployment. The sample does not post results into Telegram, so it needs no chat-send rights. If launched from a group, any `chat_instance` is context only: this example does not use it to authorize access or expose group messages. Host a deployed Mini App only behind HTTPS and configure Telegram's launch URL in a controlled bot setup; no public URL or bot is configured here.

## Telegram Business support intake and operator handoff

The same `/telegram/webhook` accepts Telegram's `business_connection`, `business_message`, `edited_business_message`, and `deleted_business_messages` update shapes after the normal webhook-secret and update-ID checks. Configure Telegram's `allowed_updates` to include these update types when registering a real webhook. The business account owner must explicitly connect a Business-enabled bot and choose the private-chat recipients and rights in Telegram. The selected-recipient controls belong to Telegram; this sample does not treat the connection as group access or an unrestricted account archive. The adapter uses only `can_reply`; it does not require message-read, deletion, profile-editing, or other Business rights.

The adapter records a synthetic ticket reference, the Telegram conversation/message reference, status, queue/operator assignment, and message change/deletion IDs. It deliberately discards message text and does not copy a transcript. New/edit/delete update delivery is de-duplicated by the existing update registry. A connected account update supplies `is_enabled` and rights; `can_reply` is true only for private conversations with an incoming message during Telegram's last-24-hour window. The sample's operator reply method checks the allowlisted operator, ticket assignment, active connection, `can_reply`, and the last inbound timestamp before calling `sendMessage` with both `business_connection_id` and the target `chat_id`. If a check fails, it returns a manual-reply instruction and does not call Telegram.

Set `TELEGRAM_BUSINESS_OPERATOR_IDS` to comma-separated synthetic or approved operator IDs before using the authorization seam. The demo has no operator UI, helpdesk queue, persistent ticket store, or real Business account; tests call the handoff/reply service directly with a fake sender. A production operator interface must authenticate employees independently, enforce assignment at the action boundary, and use durable storage, idempotency, and a retention/deletion policy. Telegram user IDs submitted by a caller are not proof of an employee role.

The Business path does not send customer data to AI or other third parties. Telegram's Bot Developer Terms require clear disclosure of retention and purpose and prohibit third-party disclosure of Business messages/files without user authorization. No Business account, real customer chat, support product, or Telegram message was used. The Bot API Business update model, connection rights and `sendMessage.business_connection_id` were checked against [Telegram's Bot API](https://core.telegram.org/bots/api#businessconnection), [connected Business bots](https://core.telegram.org/api/bots/connected-business-bots), and [Bot Developer Terms](https://telegram.org/tos/bot-developers) on 2026-11-15.

## Optional credentials (do not use for this local demo)

All credentials are environment variables. The default `TELEGRAM_SEND_ENABLED=false` keeps sends disabled. Keep it false. Setting it true enables outbound Bot API calls when a real update is handled; this repository does not ask you to do so.

The command flow is `/ai-help` → `/consent` → `/ask <question>` → inspect the bot's confirmation prompt → `/ai-confirm`. A new explicit confirmation is required for every question. `/ai-cancel` removes a staged prompt; `/stop` revokes feature consent and discards pending text. Only `/ai-confirm` can call the configured Anthropic Messages API, with exactly the submitted prompt as the user message. No history, files, group messages, other users' content, or Telegram Business messages are collected. The application holds staged text in volatile memory and does not persist prompts or answers. Anthropic states that API inputs and outputs are normally deleted from its backend within 30 days, with exceptions for legal requirements, policy enforcement, or a different agreement; see its [retention terms](https://privacy.anthropic.com/en/articles/7996866-how-long-do-you-store-my-organization-s-data). The disclosure includes this current boundary; verify the provider plan and terms before any real use. `/human` marks an in-memory follow-up request, but no operator/helpdesk is connected in this demo. This flow is a scoped example, not a determination that every deployment satisfies Telegram policy.

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
- Grafana acknowledgements are application records only; they do not change Grafana alert state, and the sample does not call Grafana APIs.
- File intake is private-chat only, requires user consent, and retains no file after processing; it is disabled unless explicitly configured.
- A Mini App launch can include limited chat context, but this sample does not use it for authorization and receives no group history or permission to send as the user.
- AI is disabled without provider credentials. Consent, staged prompts, and handoff markers are volatile in-memory demo state; every exact prompt also requires its own `/ai-confirm`. `/human` is only a marker because no operator queue is connected.
- This repository was not configured with a live bot/provider, and no live integration was verified.
- `./gradlew test` verifies local behavior only; signed Mini App fixtures are synthetic. It does not prove Telegram launch, Telegram delivery, provider connectivity, or account authorization.

## Documentation

- [Bot API](https://core.telegram.org/bots/api), [FAQ and group visibility](https://core.telegram.org/bots/faq)
- [Mini Apps and initData validation](https://core.telegram.org/bots/webapps)
- [Anthropic Messages API](https://platform.claude.com/docs/en/api/messages)
- [Telegram API Terms](https://core.telegram.org/api/terms), [Content Licensing Terms](https://telegram.org/tos/content-licensing), [Bot Developer Terms](https://telegram.org/tos/bot-developers)
- [Anthropic API data retention](https://privacy.anthropic.com/en/articles/7996866-how-long-do-you-store-my-organization-s-data) (checked 2026-11-15)
