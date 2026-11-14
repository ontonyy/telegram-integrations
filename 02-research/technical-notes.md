# Technical notes: Bot API, Business, Mini Apps, TDLib and Java

**Checked:** 2026-11-14. The official Bot API changelog currently shows **10.4**, dated 2026-11-09. Recheck it before implementation; 10.4 adds HTML widgets and ephemeral callback replies, with widgets currently restricted to private chats in production.

## Bot API

Server-to-server HTTPS interface for bot identities. Receive updates by long polling (`getUpdates`) or webhook; do not use both at once for the same bot. Webhook deployment needs public HTTPS and should validate the configured secret token header. Updates are retained for a limited period (FAQ documents up to 24 hours). Bot API supports messages, files, inline keyboards, callbacks, polls, payments and channel/group operations where bot membership/rights permit. Group privacy mode controls visibility. A bot cannot start a private chat with an arbitrary user.

Java choices: direct `java.net.http.HttpClient` for outbound-only/simple calls; TelegramBots or Pengrad library for update handling and request types; Spring Boot webhook for production service endpoints. Pin library version and confirm method coverage against the current API.

## Telegram Business

Business user connects a bot and chooses recipient scope and rights. Telegram defines the recipient selector as **private chats**; it supports existing/new chats, contacts/non-contacts, selected users and exclusions. The owner can pause or disconnect a chat. Bot API exposes business connection/update fields and connection identifiers; methods depend on granted rights and message context, including a documented 24-hour reply window after the latest incoming message. One connected business bot per account is the current documented model; connection does not require Premium. Do not infer group or channel visibility from this feature. Business message content/files must not be sent to third-party APIs without the user's authorization under Bot Developer Terms. A standard `bot token` connector does not prove Business support.

## Mini Apps

Mini Apps are web applications launched from a bot through profile/main app, keyboard/inline buttons, menu, inline mode, direct links, and (with eligibility limits) attachment menu. Direct links can open in a group and provide `chat_type` and `chat_instance` for a shared server-side workspace. They do **not** expose group messages or let the Mini App send as the user; sharing a message requires a user-selected inline result. Send raw `initData` to backend and validate the HMAC there; never trust `initDataUnsafe`. Check `auth_date` freshness. Validated Telegram identity is not your application's authorization. Java is suitable for APIs/business logic, while the embedded UI is web technology.

## TDLib Java

TDLib is Telegram's cross-platform client library. The Java interface is JNI: build TDLib with JNI enabled (official instructions use `-DTD_ENABLE_JNI=ON`) and ship compatible native libraries. TDLib is asynchronous and handles client protocol/network details. The application still implements authorization flow, update lifecycle, chat UX, local database/session protection, recovery and deletion. The user session can access the signed-in account's Telegram data available to that client. Protect the encryption key and local database, restrict operators, document revocation, and avoid unattended actions beyond the user's explicit purpose.

For a user-level search or custom client, compare whether a bot can solve the actual use case first. TDLib materially increases security and operational burden and is not just a broader Bot API token.

## Credentials and deployment

- Bot token and webhook secret: secret manager / environment, never source control.
- Telegram app `api_id`/`api_hash`, phone, login code and TDLib session: private deployment secrets/data; never commit even test sessions.
- Mini App signing validation: server only; avoid logging full launch data.
- Webhook: verify secret header, cap request size, make callbacks idempotent, acknowledge promptly, enqueue slow work.
- File intake: cap size/type, scan when appropriate, store with retention limit, clean temporary files.

## Groups, topics and privacy mode

Bot FAQ documents that a bot always receives private messages sent to it, service messages, and channel messages where it is a member. In a group, an admin or a bot with privacy mode disabled receives messages except those from other bots. With privacy mode enabled (the default), it receives commands addressed to it, certain general commands, messages sent via it, and replies directed to it. This is not a history API. Prefer explicit commands/replies/buttons for issue creation or summaries. A forum is a supergroup with separate topics; route replies with `message_thread_id`. Managing topics requires the corresponding admin right.

## Payments

For digital goods/services sold through bots or Mini Apps, Telegram's Bot Developer Terms require Telegram Stars. Physical goods/services can use a third-party payment provider through Bot Platform payment APIs, subject to provider availability and terms. Check current app-store policy before launch.

## Official references and Java projects

- [Bot API](https://core.telegram.org/bots/api), [changelog](https://core.telegram.org/bots/api-changelog), [FAQ](https://core.telegram.org/bots/faq).
- [Business](https://core.telegram.org/api/business), [connected bots](https://core.telegram.org/api/bots/connected-business-bots), [BusinessBotRights](https://core.telegram.org/constructor/businessBotRights).
- [Mini Apps](https://core.telegram.org/bots/webapps), [TDLib](https://core.telegram.org/tdlib/), [TDLib Java API](https://core.telegram.org/tdlib/Java_API), [Java example](https://github.com/tdlib/td/tree/master/example/java).
- [Forum topics](https://core.telegram.org/api/forum), [Business bot recipients](https://core.telegram.org/constructor/businessBotRecipients).
- [TelegramBots](https://github.com/rubenlagus/TelegramBots), [Pengrad Java Telegram Bot API](https://github.com/pengrad/java-telegram-bot-api).

**Documented:** mechanisms and API behavior described in linked references. **Implementation notes:** operational guidance and Java service shapes are recommended patterns; validate against the selected API/library versions.
