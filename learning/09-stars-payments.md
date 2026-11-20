# 9. Simulate a Telegram Stars payment lifecycle

**Goal:** trace an XTR order from checkout validation through fulfillment and refund, while keeping the provider boundary fake.

Telegram requires a bot to answer a `pre_checkout_query` within ten seconds. The bot must validate the order before approving it, and a digital good must only be delivered after Telegram sends `successful_payment`. These updates are separate: an approved checkout is not proof of payment. A Stars refund uses `refundStarPayment`; a successful API result is the confirmation. Read the [official Stars payment guide](https://core.telegram.org/bots/payments-stars) and the [Bot API reference](https://core.telegram.org/bots/api#answerprecheckoutquery) before designing a live integration.

## Walk the example

1. Read [the Java example guide](../tasks/completed-issues/J19.md) and the [Spring README](../examples/java-spring/README.md#simulated-telegram-stars-lifecycle).
2. Inspect `TelegramStarsPaymentService`, `TelegramStarsGateway`, and `SimulatedTelegramStarsGateway` in `examples/java-spring/src/main/java/example/telegram/`.
3. Follow `TelegramWebhookController` handling for `pre_checkout_query` and `message.successful_payment`.
4. Run `./gradlew test --tests example.telegram.TelegramStarsPaymentServiceTest --tests example.telegram.TelegramWebhookControllerTest` from `examples/java-spring/`.

`/buy-demo` only creates an in-memory local order reference. It does not call `sendInvoice`, so no real Telegram checkout can begin. Tests inject synthetic updates and a recording gateway. The service checks buyer, payload, currency, amount, and expiry; delivery is only a synthetic entitlement after a valid successful payment. Repeat updates and charge identifiers are handled idempotently. Refunds are restricted to the order owner. An ambiguous refund result becomes `REFUND_UNKNOWN`; it is not retried automatically because a repeated refund request could have a different outcome.

## What would change for a live system?

The current order, charge, and update records are bounded process memory. Restart loses them and multiple instances do not share them. A real integration needs durable, transactionally updated order and idempotency records, a real invoice sender, secure webhook ingress, reconciliation for ambiguous refunds, operational monitoring, and a support path. The test clock's ten-second check is not a measurement of network latency or Telegram's end-to-end deadline. This repository has not connected a bot or payment provider, sent an invoice, accepted a payment, or refunded Stars.

**Checkpoint:** Why is a valid `pre_checkout_query` insufficient to grant the product? What should an operator do when a refund call times out with an unknown result?
