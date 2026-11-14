# Technical notes: Bot API, Business, Mini Apps, TDLib and Java

**Checked:** 2026-11-14. Target the API version visible on the official docs at implementation time; the Bot API changelog showed 10.3 on this review date.

## Bot API

Server-to-server HTTPS interface for bot identities. Receive updates by long polling (`getUpdates`) or webhook; do not use both at once for the same bot. Webhook deployment needs public HTTPS and should validate the configured secret token header. Updates are retained for a limited period (FAQ documents up to 24 hours). Bot API supports messages, files, inline keyboards, callbacks, polls, payments and channel/group operations where bot membership/rights permit. Group privacy mode controls visibility. A bot cannot start a private chat with an arbitrary user.

Java choices: direct `java.net.http.HttpClient` for outbound-only/simple calls; TelegramBots or Pengrad library for update handling and request types; Spring Boot webhook for production service endpoints. Pin library version and confirm method coverage against the current API.

## Telegram Business

Business user connects a bot and chooses recipient scope and rights. Bot API exposes business connection/update fields and business connection identifiers; reply/edit behavior depends on granted rights and recipient/message context. The right to reply is scoped by Telegram's documented recent-message window (24 hours). Product vendors must explicitly document this mode; a conventional `bot token` connector may only support ordinary bot conversations. One connected business bot per account is the current documented model. Business account features and Premium eligibility/pricing can vary by market; connected bots are documented as available without Premium.

## Mini Apps

Mini Apps are web applications launched from a bot. Use chat buttons/links for simple flows and a Mini App for richer UI. Send `initData` to backend and validate its signature there; never trust `initDataUnsafe` or browser-supplied user ID for authorization. Treat the backend as source of truth for order price, user role and action permission. Java is suitable for APIs/business logic, while the embedded UI is web technology.

## TDLib Java

TDLib is Telegram's cross-platform client library. The Java interface is JNI: build TDLib with JNI enabled (official instructions use `-DTD_ENABLE_JNI=ON`) and ship compatible native libraries. TDLib is asynchronous and handles client protocol/network details. The application still implements authorization flow, update lifecycle, chat UX, local database/session protection, recovery and deletion. The user session can access the signed-in account's Telegram data available to that client. Protect the encryption key and local database, restrict operators, document revocation, and avoid unattended actions beyond the user's explicit purpose.

For a user-level search or custom client, compare whether a bot can solve the actual use case first. TDLib materially increases security and operational burden and is not just a broader Bot API token.

## Credentials and deployment

- Bot token and webhook secret: secret manager / environment, never source control.
- Telegram app `api_id`/`api_hash`, phone, login code and TDLib session: private deployment secrets/data; never commit even test sessions.
- Mini App signing validation: server only; avoid logging full launch data.
- Webhook: verify secret header, cap request size, make callbacks idempotent, acknowledge promptly, enqueue slow work.
- File intake: cap size/type, scan when appropriate, store with retention limit, clean temporary files.

## Official references and Java projects

- [Bot API](https://core.telegram.org/bots/api), [changelog](https://core.telegram.org/bots/api-changelog), [FAQ](https://core.telegram.org/bots/faq).
- [Business](https://core.telegram.org/api/business), [connected bots](https://core.telegram.org/api/bots/connected-business-bots), [BusinessBotRights](https://core.telegram.org/constructor/businessBotRights).
- [Mini Apps](https://core.telegram.org/bots/webapps), [TDLib](https://core.telegram.org/tdlib/), [TDLib Java API](https://core.telegram.org/tdlib/Java_API), [Java example](https://github.com/tdlib/td/tree/master/example/java).
- [TelegramBots](https://github.com/rubenlagus/TelegramBots), [Pengrad Java Telegram Bot API](https://github.com/pengrad/java-telegram-bot-api).

**Documented:** mechanisms and API behavior described in linked references. **Implementation notes:** operational guidance and Java service shapes are recommended patterns; validate against the selected API/library versions.
