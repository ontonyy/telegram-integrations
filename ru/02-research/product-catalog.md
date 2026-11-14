# Каталог продуктов и коннекторов

**Проверено:** 14 ноября 2026 года. Все строки проверены на эту дату. Цены взяты со страниц поставщиков; валюта, период оплаты, регион, налоги, акции, AI, количество мест и лимиты могут меняться. Перед покупкой проверьте условия оформления.

| Продукт | Подключение Telegram и функции | Снимок цены / модель затрат | Ограничения |
|---|---|---|---|
| [Kommo Telegram](https://www.kommo.com/integrations/telegram/) | Нативный бот-коннектор: входящий чат создаёт лид, история хранится в карточке, ответ из CRM, назначение и Salesbot | Base $25, Advanced $35, Pro $45 за пользователя в месяц; страница указывает минимум 6 месяцев оплаты | Токен BotFather, не личный аккаунт. Ограничения воронки/автоматизации зависят от плана. |
| [Приложение Kommo от Whatcrm](https://www.kommo.com/integrations/telegram-from-whatcrm/) | Сторонний каталог заявляет вход по QR/телефону, синхронизацию чатов аккаунта и несколько аккаунтов | Условия стороннего приложения | Не официальный продукт Telegram или нативный коннектор Kommo. Доступ к сеансу аккаунта чувствителен; аудит хранения и удаления не проведён, заявленные функции независимо не проверялись. |
| [Chatwoot](https://www.chatwoot.com/features/telegram-integration) | Общий inbox для бота, назначение, заметки, метки, автоматизация, вложения; Chatwoot указывает Business Bot с v4.3 и автоматическое определение режима | Cloud: Free $0/оператор до 2 операторов/500 диалогов, но Telegram не входит в Free; Startups $19/оператор в месяц, Business $39. AI отдельно (на странице $20/1000 кредитов). Community Edition self-hosted — MIT, сервер оплачивается отдельно. | Окно ответа 24 часа. Chatwoot описывает возможную путаницу имени отправителя, если тот же бот использовался в обычном режиме. Для Business используйте отдельного бота/inbox и проверяйте типы сообщений. Для self-hosted нужны эксплуатация и безопасность. |
| [Intercom Telegram](https://www.intercom.com/changes/en/152537-telegram-is-now-a-supported-channel) | Канал Telegram Bot в Inbox; текст, фото, файлы, голосовые; Workflows, тикеты, CSAT и Fin AI | Essential $29/место/месяц при годовой оплате (промо может показывать $19), Advanced $85, Expert $132; Fin — $0.99 за результат | Настройка использует токен бота/webhook. Поддержку Business предполагать нельзя. AI и лицензии оплачиваются отдельно. |
| [respond.io](https://respond.io/help/channels) | Канал бота Telegram в общем inbox; у продукта есть мобильное приложение, автоматизация и рассылки | Starter $79/мес., Growth $159, Advanced $279; показаны также годовые суммы, тариф зависит от активных контактов | Проверяйте конкретный план и лимиты. Считать канал ботом, пока поставщик не подтвердит обратное. |
| [Make Telegram Bot](https://www.make.com/integrations/telegram) | Триггеры и действия с обновлениями, сообщениями и медиа; страница помечает модули как поддерживаемые сообществом | Free 1000 кредитов/месяц; на снимке интерфейса Core $12, Pro $21, Teams $38 в месяц за 10k кредитов; период оплаты влияет | Плата за операции; бесплатный план запускает минимум раз в 15 минут. Проверьте поддержку webhook и владельца модулей. |
| [Zapier Telegram](https://zapier.com/apps/telegram/integrations) | Триггер нового сообщения от бота; отправка сообщения, фото, опроса, реакции | Free $0/100 задач в месяц; Professional от $19.99 (750 задач, годовая оплата); Team $69 | Нужен bot token; есть лимиты задач и правила премиум-приложений. |
| [n8n Telegram node](https://docs.n8n.io/integrations/builtin/app-nodes/n8n-nodes-base.telegram/) | Официальный node и Telegram Trigger: сообщения, файлы, чаты, callback | Cloud Starter €20/мес. годовой оплаты за 2.5k запусков; Pro €50 за 10k; Business self-hosted €667 за 40k; Community self-hosted без SaaS-платы | У Community Sustainable Use License: доступен исходный код, но это не OSI open source. Хостинг и безопасность остаются на владельце. |
| [Grafana Telegram contact point](https://grafana.com/docs/grafana/latest/alerting/configure-notifications/manage-contact-points/integrations/configure-telegram/) | Отправляет алерты в чат через bot token/chat ID | Grafana OSS без цены лицензии (AGPL-3.0), но нужна инфраструктура; облако тарифицируется по использованию | Только исходящие уведомления; документирован лимит Telegram 4096 символов; согласование не встроено. |
| [Zapier MCP для Telegram](https://zapier.com/mcp/telegram) | MCP-действия: реакция, сообщение, фото, опрос | Страница указывает 2 задачи Zapier за вызов инструмента; действует общий тариф | Только разрешённые действия, история чатов не предоставляется. AI-клиент видит лишь открытые инструменты. |
| [Chatwoot AgentBot](https://app.chatwoot.com/hc/user-guide/articles/1677497472-how-to-use-agent-bots) | Webhook подключает внешнюю логику/AI; бот может передать диалог оператору | Тариф/хостинг Chatwoot плюс модель | Доступ модели к содержимому требует соблюдения Telegram-условий и согласия клиента. |

## Открытый код и Java-проекты

- [TDLib](https://github.com/tdlib/td): официальная клиентская библиотека Telegram, Boost Software License; Java JNI, требуется native-сборка. Это технология пользовательского клиента, а не обёртка Bot API.
- [TelegramBots](https://github.com/rubenlagus/TelegramBots): Java-библиотека Bot API, MIT, polling/webhook и Spring Boot starters. Перед выбором проверьте актуальную версию API и Spring.
- [Pengrad Java Telegram Bot API](https://github.com/pengrad/java-telegram-bot-api): Java HTTP-клиент, MIT; совместимость методов и релиза нужно проверить перед фиксацией версии.
- [Chatwoot](https://github.com/chatwoot/chatwoot): open-source helpdesk, ядро MIT (для Enterprise-компонентов отдельные условия).
- [Grafana](https://github.com/grafana/grafana): AGPL-3.0, включает алертинг.
- [n8n](https://github.com/n8n-io/n8n): исходный код доступен по Sustainable Use License; это не лицензия OSI open source.
- [Сервер Telegram Bot API](https://github.com/tdlib/telegram-bot-api): локальный endpoint Bot API, Boost; не превращает бота в пользовательский аккаунт.

## Ещё один вариант CRM

HubSpot можно связать через [Zapier](https://zapier.com/apps/hubspot/integrations/telegram) или [Make](https://www.make.com/integrations/hubspotcrm/telegram); коннектор использует бота. На странице цен HubSpot указаны бесплатные CRM-возможности до 2 пользователей и 1000 контактов; дальнейшие планы платные, перепроверьте упаковку. Эти интеграции подтверждают совместимость приложений, но не являются готовым Telegram helpdesk.

## Страницы тарифов для этого снимка

[Kommo](https://www.kommo.com/buy/tariff/), [Chatwoot Cloud](https://www.chatwoot.com/pricing), [Chatwoot self-hosted](https://www.chatwoot.com/pricing/self-hosted-plans), [Intercom](https://www.intercom.com/pricing), [respond.io](https://respond.io/pricing), [Make](https://www.make.com/pricing), [Zapier](https://zapier.com/pricing), [n8n](https://n8n.io/pricing/), [Grafana Cloud](https://grafana.com/docs/grafana-cloud/platform/pricing-and-usage/), [HubSpot CRM](https://www.hubspot.com/pricing/crm).

Каталог — отправная точка сравнения, а не рекомендация по безопасности. Страницы поставщиков могут локализоваться и меняться.
