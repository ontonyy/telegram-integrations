# Шаг 4 — Добавить веб-интерфейс через Mini Apps

## Цель обучения

Понять, когда кнопок и сообщений уже недостаточно, как запуск Mini App связывает Telegram с веб-интерфейсом и почему backend должен проверить identity и права.

## Ментальная модель

```mermaid
sequenceDiagram
  participant Person as Пользователь
  participant TG as Клиент Telegram
  participant Web as Frontend Mini App
  participant API as Java backend
  Person->>TG: открыть Mini App
  TG->>Web: данные запуска (initData)
  Web->>API: запрос + исходная initData
  API->>API: проверить подпись и срок
  API->>API: проверить доступ к записи/действию
  API-->>Web: результат в разрешённом объёме
```

Данные запуска Telegram помогают backend установить, какой пользователь открыл приложение, если сервер проверит их подпись. Но это не доказывает, что пользователь владеет любым заказом или может что-то согласовать. Приложение должно самостоятельно проверить доступ к своим записям и действиям. Контекст запуска из группы не создаёт общего доступа и не открывает историю переписки.

## Прочитать и посмотреть

1. Прочитайте [технические заметки Mini Apps](../02-research/technical-notes.md) и [официальную документацию Mini Apps](https://core.telegram.org/bots/webapps).
2. Изучите [`TelegramInitDataValidator`](../../examples/java-spring/src/main/java/example/telegram/TelegramInitDataValidator.java), [`MiniAppController`](../../examples/java-spring/src/main/java/example/telegram/MiniAppController.java) и [тесты контроллера](../../examples/java-spring/src/test/java/example/telegram/MiniAppControllerTest.java).
3. Прочитайте [J07](../../tasks/completed-issues/J07.md) и инструкции frontend/backend в [README примера](../../examples/java-spring/README.md).

## Пример применения

Оставьте короткие линейные действия сообщениям и кнопкам. Mini App используйте для структурированных форм, пошагового заказа, панели или совместного выбора. Передавайте исходную `initData` backend, проверяйте HMAC и возраст там, а доступ к каждому ресурсу проверяйте отдельно. Для реального запуска нужны HTTPS и постоянное хранилище.

## Самопроверка

Почему `initDataUnsafe.user.id` недостаточно для авторизации? Ответ должен включать проверку подписи на сервере и отдельную проверку владельца записи.

**Связанная задача:** J07. Тесты используют синтетические подписанные данные; живой запуск Mini App, публичный URL и production-система прав не настраивались.
