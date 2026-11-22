# 10. Добавить Telegram Login OIDC на сайт

**Цель:** понять, как обычный сайт может войти через OpenID Connect провайдера Telegram и почему это отличается от доверительной схемы Mini App.

Telegram Login описывает OpenID Connect Authorization Code Flow с PKCE. Браузер перенаправляется в Telegram, Telegram возвращает authorization code на зарегистрированный callback, а сервер обменивает его на токены. Поддерживаемая OIDC-библиотека проверяет подпись и claims ID token; приложение отдельно решает, какие действия разрешить подтверждённой identity. См. [Telegram Login](https://core.telegram.org/bots/telegram-login), [Spring Security OAuth2 Login](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/) и [конфигурацию OAuth2-клиента Spring Boot](https://docs.spring.io/spring-boot/reference/security/oauth2.html).

## Изучите пример

1. Прочитайте [описание Java-примера](../tasks/completed-issues/J20.md) и [раздел README Spring](../examples/java-spring/README.md#telegram-login-oidc-для-сайта).
2. Изучите `TelegramLoginOidcConfiguration` и `TelegramLoginOidcController` в `examples/java-spring/src/main/java/example/telegram/`.
3. Откройте `examples/java-spring/src/main/resources/static/telegram-login-demo.html`.
4. Запустите `./gradlew test --tests example.telegram.TelegramLoginOidcSecurityTest` из `examples/java-spring/`.

Необязательная функция по умолчанию выключена. Issuer по умолчанию указывает на OIDC discovery Telegram; запрашивается только `openid`. Client ID и secret передаются серверу через окружение; тесты используют синтетические значения и issuer на loopback. Redirect по умолчанию — `{baseUrl}/login/oauth2/code/telegram`; если публичный origin нельзя надёжно определить, задайте фиксированный зарегистрированный URI через `TELEGRAM_LOGIN_OIDC_REDIRECT_URI`. Тест проходит через redirect и callback, проверяет PKCE S256 и привязку redirect URI, а также отклоняет неверные state, nonce, подпись, issuer, audience и срок действия. У Telegram нет отдельного UserInfo endpoint, поэтому пример использует проверенный ID token и возвращает только стабильный `sub` через `/login-demo/me`.

## Не смешивайте границы identity

Telegram Login OIDC предназначен для обычного сайта. Mini App отправляет backend исходную строку `Telegram.WebApp.initData`; backend проверяет HMAC и свежесть Telegram-подписи, а затем получает user ID. Не заменяйте один механизм другим и не считайте OIDC `sub`, username, телефон или profile claim ролью приложения, членством в компании или разрешением на доступ к ресурсам.

По умолчанию не запрашиваются scopes `profile`, `phone` и `telegram:bot_access`. Добавляйте дополнительные scopes только при документированной необходимости и понятном разрешении пользователя. Client secret остаётся на сервере. До отдельно разрешённого live-запуска зарегистрируйте точный origin сайта и callback `/login/oauth2/code/telegram` в @BotFather.

Пример использует серверную HTTP-сессию Spring для хранения authorization state и входа. Общие сессии, cookie policy, связывание аккаунтов, роли, logout и production-настройка остаются решениями приложения. Тесты не подтверждают live discovery Telegram, регистрацию клиента, redirect allowlist или живой вход.

**Проверьте себя:** от чего PKCE защищает при обмене authorization code? Какое решение сайт обязан принять после проверки Telegram `sub`?
