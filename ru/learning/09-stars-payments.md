# 9. Смоделировать цикл оплаты Telegram Stars

**Цель:** проследить заказ XTR от проверки checkout до выдачи права и возврата, сохраняя платёжную границу fake.

Telegram требует ответить на `pre_checkout_query` в течение десяти секунд. Перед подтверждением бот должен проверить заказ, а цифровой товар можно выдавать только после получения `successful_payment`. Это разные обновления: одобренный checkout не доказывает оплату. Возврат Stars выполняется методом `refundStarPayment`; успешный ответ API служит подтверждением. Перед проектированием живой интеграции прочитайте [официальное руководство по Stars](https://core.telegram.org/bots/payments-stars) и [справочник Bot API](https://core.telegram.org/bots/api#answerprecheckoutquery).

## Изучите пример

1. Прочитайте [описание Java-примера](../tasks/completed-issues/J19.md) и [README Spring](../examples/java-spring/README.md#смоделированный-цикл-оплаты-telegram-stars).
2. Изучите `TelegramStarsPaymentService`, `TelegramStarsGateway` и `SimulatedTelegramStarsGateway` в `examples/java-spring/src/main/java/example/telegram/`.
3. Проследите обработку `pre_checkout_query` и `message.successful_payment` в `TelegramWebhookController`.
4. Запустите `./gradlew test --tests example.telegram.TelegramStarsPaymentServiceTest --tests example.telegram.TelegramWebhookControllerTest` из каталога `examples/java-spring/`.

`/buy-demo` только создаёт ссылку на локальный заказ в памяти. Метод `sendInvoice` не вызывается, поэтому настоящий Telegram checkout не начинается. Тесты передают синтетические обновления и recording gateway. Сервис проверяет покупателя, payload, валюту, сумму и срок действия; синтетическое право выдаётся лишь после правильного успешного платежа. Повторные update и charge обрабатываются идемпотентно. Запрос возврата доступен только владельцу заказа. Неоднозначный результат возврата получает статус `REFUND_UNKNOWN` и не повторяется автоматически: повторный вызов может иметь иной результат.

## Что потребуется для живой системы?

Сейчас заказ, charge и обработанные update хранятся ограниченно и только в памяти процесса. Перезапуск стирает состояние, а несколько экземпляров не делят его. Живой интеграции нужны постоянное хранилище с транзакционными заказами и idempotency-записями, отправитель настоящего invoice, защищённый webhook, сверка неоднозначных возвратов, мониторинг и канал поддержки. Проверка десятисекундного срока по тестовым часам не измеряет сетевую задержку или end-to-end время Telegram. Репозиторий не подключал бота или платёжного провайдера, не отправлял invoice, не принимал оплату и не возвращал Stars.

**Проверьте себя:** почему одного валидного `pre_checkout_query` недостаточно для выдачи товара? Что должен сделать оператор, если запрос возврата завершился тайм-аутом с неизвестным результатом?
