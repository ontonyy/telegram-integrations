# Telegram integration ecosystem research

**Index updated:** 2026-11-15. This is a working reference for product selection and future integration examples. Telegram capabilities, vendor plans, and prices change; re-check linked source pages before implementation or purchase.

## Start here

- [Capability map and comparison](capability-map.md) — Bot API, Business, Mini Apps, TDLib, connectors, MCP.
- [Groups, topics and shared Mini Apps](groups-and-topics.md) — privacy mode, explicit actions, forum topics and shared group context.
- [Customer support](support.md) — bot vs Business vs helpdesk, handoff and sender identity.
- [Sales and CRM](sales-crm.md) — lead capture, assignment, reminders, CRM connectors.
- [Customer services, orders and payments](customer-services.md) — appointments, status, checkout and Stars.
- [Managers and leadership](managers.md) — mobile reports, alerts, approvals, with and without AI.
- [Internal processes](internal-processes.md) — employee requests, IT, approvals and knowledge.
- [Marketing and communities](marketing.md) — channels, opt-in, broadcasts and limits.
- [AI and Telegram data](ai-and-data.md) — consent boundaries and data access by integration type.
- [Claude, Codex and Telegram](ai-codex-claude.md) — model interface vs Telegram data source, Anthropic's official Telegram channel preview, Codex app-server and environment snapshot.
- [Technical integrations](technical-integrations.md) — GitHub, Jira, CI/CD, Grafana, Java/Spring.
- [Product and connector catalog](product-catalog.md) — concrete tools, verified functions, pricing snapshots and caveats.
- [Implementation landscape](implementation-landscape.md) — official Claude Code channel and source-reviewed coding-agent bridges, MCP/TDLib history readers, support product paths, practical patterns and security boundaries.
- [Scenario catalog](scenario-catalog.md) — reusable end-to-end patterns and complexity.
- [Technical notes](technical-notes.md) — Bot API, Telegram Business, Mini Apps, TDLib and Java.
- [Runnable Java/Spring example](../examples/java-spring/README.md) — dry-run webhook, Mini App signature validation, optional consent-gated Claude API call.
- [Optional TDLib Java account-client example](../examples/tdlib-java/README.md) — fake-tested authorization flow and isolated official JNI adapter; real account use is manual and separate from tests.

## How to read the findings

Each scenario distinguishes **documented** capabilities from a **proposed architecture**. Product pages establish only the features the vendor documents; they do not prove access to every Telegram chat or account. “Telegram integration” often means a bot token, not authorization as a user's client.

Telegram-origin message content should not be sent to an AI system by default. See [AI and Telegram data](ai-and-data.md) and review the current terms and consent conditions for the exact use.

## Source policy

Prefer official Telegram, product, pricing, and project documentation. Pricing below is a dated page snapshot, often excluding taxes, usage, implementation, hosting, or region-specific terms. Validate the current plan and eligibility with the vendor before budgeting.

**Checked date for every document and catalog entry:** 2026-11-14.
