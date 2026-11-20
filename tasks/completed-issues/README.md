# Completed Java integration issues

**Checked:** 2026-11-15. These notes explain what each closed issue delivered, how the example works, and where the pattern fits in a real project. They describe repository code and its local verification; they do not imply that a live Telegram, CRM, or vendor account was connected.

For a learning sequence organized by concept (rather than issue number), start with the [Telegram integrations learning path](../../learning/README.md). The pages below are implementation references that complement its eight lessons.

Russian versions are in [`ru/tasks/completed-issues`](../../ru/tasks/completed-issues/README.md).

| Issue | Completed work | Guide |
|---|---|---|
| J01 | Credential-free Java foundation and mock seams | [J01](J01.md) |
| J02 | Direct Bot API sender with Java HttpClient | [J02](J02.md) |
| J03 | Protected webhook and update de-duplication | [J03](J03.md) |
| J04 | Group vote with authorized callbacks | [J04](J04.md) |
| J05 | Telegram Business support handoff | [J05](J05.md) |
| J06 | GitHub Actions build notifications | [J06](J06.md) |
| J07 | Mini App form and Java backend | [J07](J07.md) |
| J08 | Jira Cloud event notifications | [J08](J08.md) |
| J09 | Grafana alert and app-level acknowledgement | [J09](J09.md) |
| J10 | Consented, bounded file intake | [J10](J10.md) |
| J11 | Direct-to-bot AI question with consent | [J11](J11.md) |
| J12 | Optional TDLib Java account-client example | [J12](J12.md) |
| J13 | Telegram-to-coding-agent bridge research | [J13](J13.md) |
| J14 | Consent-based HubSpot lead capture | [J14](J14.md) |
| J15 | Private employee request and approval | [J15](J15.md) |
| J16 | Durable notification outbox, retry policy, and expiry | [J16](J16.md) |
| J17 | Chatwoot Telegram customer-support handoff walkthrough and mock seam | [J17](J17.md) |
| J18 | Shared group Mini App, exact application allowlist, and versioned availability updates | [J18](J18.md) |
| J19 | Synthetic Telegram Stars checkout, fulfillment, refund, and support lifecycle | [J19](J19.md) |

Start with [the Java/Spring README](../../examples/java-spring/README.md) for commands, configuration names, and the shared demo boundaries. J13 is a research decision, not a runnable bridge. J12 is isolated and is not part of the normal Spring startup. J17's built-in Chatwoot Telegram channel is separate from its fake-backed Java choreography seam.
