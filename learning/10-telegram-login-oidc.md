# 10. Add Telegram Login OIDC to a website

**Goal:** understand how a conventional website can sign a user in through Telegram's OpenID Connect provider, and why this is a different trust flow from a Mini App.

Telegram Login documents OpenID Connect Authorization Code Flow with PKCE. The browser is redirected to Telegram, Telegram returns an authorization code to a registered callback, and the server exchanges it for tokens. A maintained OIDC library validates the ID token signature and claims; the application must still decide what that identity may do. See [Telegram Login](https://core.telegram.org/bots/telegram-login), [Spring Security OAuth2 Login](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/), and [Spring Boot OAuth2 client configuration](https://docs.spring.io/spring-boot/reference/security/oauth2.html).

## Walk the example

1. Read [the Java example guide](../tasks/completed-issues/J20.md) and [the Spring README section](../examples/java-spring/README.md#telegram-login-oidc-for-a-website).
2. Inspect `TelegramLoginOidcConfiguration` and `TelegramLoginOidcController` under `examples/java-spring/src/main/java/example/telegram/`.
3. Open `examples/java-spring/src/main/resources/static/telegram-login-demo.html`.
4. Run `./gradlew test --tests example.telegram.TelegramLoginOidcSecurityTest` from `examples/java-spring/`.

The optional feature is disabled by default. The issuer defaults to Telegram's OIDC discovery endpoint and the only requested scope is `openid`. Client ID and secret are server-side environment settings; tests use synthetic values against a loopback-only issuer. The redirect defaults to `{baseUrl}/login/oauth2/code/telegram`; set `TELEGRAM_LOGIN_OIDC_REDIRECT_URI` to a fixed registered URI when the app's public origin cannot be derived safely. The test follows the authorization redirect and callback, verifies S256 PKCE and redirect URI binding, and rejects mismatched state, nonce, signature, issuer, audience, or expiry. Telegram does not expose a separate UserInfo endpoint, so the example uses the verified ID token and returns only its stable `sub` from `/login-demo/me`.

## Keep the identity boundary clear

Telegram Login OIDC is for a normal website. A Mini App instead sends raw `Telegram.WebApp.initData` to its backend, which validates Telegram's HMAC and freshness before deriving the user ID. Do not exchange one mechanism for the other or treat an OIDC `sub`, username, phone number, or profile claim as an application role, company membership, or resource permission.

The default asks for no `profile`, `phone`, or `telegram:bot_access` scope. Add optional scopes only when the app has a documented need and the user knowingly authorizes the request. Client secret stays on the server. Register the exact website origin and `/login/oauth2/code/telegram` callback in @BotFather before any separately approved live setup.

This example uses Spring's server-side HTTP session to retain authorization state and login. Session sharing, cookie policy, account linking, authorization roles, logout behavior, and production deployment hardening remain application decisions. Tests do not prove Telegram's live discovery, client registration, redirect allowlist, or login behavior.

**Checkpoint:** What does PKCE protect in the authorization-code exchange? Which decision must the website make after it has verified Telegram's `sub`?
