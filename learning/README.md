# Telegram integrations learning path

**Audience:** a developer who wants to understand Telegram integration choices and learn the repository examples in a useful order. **Outcome:** choose an integration mechanism, trace its data and permissions, then adapt the closest Java example without confusing a mock with a live integration.

Start with Step 1 and continue in order. Each lesson gives you a concept, a code-reading route, a real-project shape, and a checkpoint. You do not need to build every example. The [Russian learning path](../ru/learning/README.md) follows the same sequence.

## The path

| Step | Learn | Use when | Repository examples |
|---|---|---|---|
| [1. Choose the Telegram surface](01-choose-a-surface.md) | Bot API, Business, Mini Apps, TDLib, automation tools | Before choosing a product architecture | J01, J12, J13 |
| [2. Build the bot service boundary](02-bot-service.md) | Outbound HTTP, webhook, secrets, retries, idempotency, outbox | Commands, notifications, service integration | J01–J03, J16 |
| [3. Model people and chat permissions](03-chat-workflows.md) | Private chats, groups, Business support, roles, handoff | Support, approvals, votes, internal requests | J04, J05, J15 |
| [4. Add a web interface with Mini Apps](04-mini-apps.md) | Launch context, signed identity, backend authorization, shared state and concurrency | Forms, dashboards, shared workflows | J07, J18 |
| [5. Connect business and operations systems](05-connect-business-systems.md) | Webhooks, filters, acknowledgements, CRM, CI and reliable delivery | Sales, incidents, project delivery | J06, J08, J09, J14, J16 |
| [6. Handle files and AI deliberately](06-files-and-ai.md) | Consent, file boundaries, direct-to-bot AI, data policy | Customer self-service and document workflows | J10, J11 |
| [7. Understand user clients and coding-agent bridges](07-tdlib-and-agents.md) | TDLib sessions, account access, agent isolation | A real user-client requirement or remote development pilot | J12, J13 |
| [8. Run customer support through Chatwoot](08-chatwoot-support.md) | Standard Telegram bot vs Business bot, operator handoff, stored history, rights and reply window | A team needs a shared support inbox | J05, J17 |
| [9. Simulate Telegram Stars payments](09-stars-payments.md) | XTR invoice lifecycle, pre-checkout deadline, fulfillment, idempotency, refunds and support | A product needs a payment-flow prototype before provider wiring | J19 |

## How to study each step

1. Read the lesson's mental model before opening code.
2. Follow its **Read and inspect** links. Start with the README, then one service, then its tests.
3. Trace one synthetic request from input to stored state or outgoing adapter.
4. Answer the checkpoint in your own words. If you cannot, revisit the cited section.
5. Use the linked issue guide when you need exact completion details or test boundaries.

## Important distinction: demo, verified code, live connection

The Java examples are implemented and locally testable with synthetic payloads, fake adapters, or mock HTTP. That does not mean this repository has connected a customer bot, Business account, CRM tenant, production webhook, user account, or AI provider. Each lesson calls out where a real deployment needs additional configuration, durable storage, identity, privacy review, and operational controls.

## Suggested learning tracks

- **I need notifications:** Steps 1 → 2 → 5 (J02/J06/J09).
- **I am building support or an internal workflow:** Steps 1 → 2 → 3; add Step 4 for structured forms.
- **I am adding sales or CRM capture:** Steps 1 → 2 → 3 → 5 (J14).
- **I am exploring AI over Telegram:** Steps 1 → 2 → 6 → 7. Do not skip the consent and data-source boundary.
- **I want Telegram account access:** Steps 1 → 7. A bot token is not a user session; TDLib is a separate client architecture.

## Reference catalogs

- [Completed issue guides](../tasks/completed-issues/README.md) — what each J01–J17 issue delivered.
- [Capability map](../02-research/capability-map.md) — comparison of Telegram integration surfaces.
- [Implementation landscape](../02-research/implementation-landscape.md) — products and agent bridges, with evidence boundaries.
- [Java/Spring example](../examples/java-spring/README.md) and [TDLib example](../examples/tdlib-java/README.md) — run and configuration instructions.
- [Telegram technical notes](../02-research/technical-notes.md) — platform-level reference links and constraints.

**Checked:** 2026-11-15. Recheck platform rules and third-party product details before a real deployment.
