# Customer services, orders and payments

**Checked:** 2026-11-14.

## Choose chat controls or Mini App

Use a message and inline buttons for a small choice, status check, confirmation, or reminder. Use a Mini App when a customer needs a searchable catalog, multiple options, calendar, address form, item customization, or a multi-step flow. A Mini App is a web frontend launched through a bot; backend validation, pricing, inventory, and order state remain server-side.

## Example flow

Customer opens a bot → chooses “Book” or “Order” → simple selection stays in chat, complex options open Mini App → Java/web backend validates request and stores order → CRM/booking/payment provider confirms → bot sends status and reminder. The customer can check status by authenticated order reference or a signed account-bound link; do not rely on an editable client parameter alone.

## Payment rules

- **Digital goods/services sold inside Telegram:** Telegram's documented Bot Platform rule requires Telegram Stars (`XTR`) for digital goods/services. Invoice → pre-checkout validation (answer within 10 seconds) → successful payment event → deliver. Provide `/paysupport` and refund handling. This includes digital purchases even where an external site/app is also available; evaluate hybrid offerings individually.
- **Physical goods/services:** Bot Payments supports third-party payment providers for physical goods/services. Telegram says it does not collect a fee; provider fees, disputes and customer support remain with the developer/provider. Handle shipping, tax and payment status with the provider.
- **Mixed offer:** a service that includes digital functionality can be hard to classify. Confirm the current Stars policy before launch; do not route an in-app digital purchase around the rule with an external checkout.

Stars value/payout and provider fees vary. Do not hardcode a USD conversion in a product model.

## Implementation options

- Simple appointment choices: Bot API + calendar/booking connector, with confirmation and reminders.
- Rich booking or storefront: Mini App + backend + database + payment provider; Bot API sends lifecycle notifications.
- Digital content or subscriptions: Bot API invoice in Stars, fulfil only after `successful_payment`, with support and refund controls.
- Physical item: Mini App or chat to collect order details, provider checkout, then track fulfilment.

**Documented:** Telegram describes the Mini App launch context and distinct digital vs physical payment mechanisms. **Proposed:** the order state machine, CRM linkage, inventory and reminder architecture.

Sources: [Mini Apps](https://core.telegram.org/bots/webapps), [Stars payments](https://core.telegram.org/bots/payments-stars), [physical goods payments](https://core.telegram.org/bots/payments), [Bot API](https://core.telegram.org/bots/api).
