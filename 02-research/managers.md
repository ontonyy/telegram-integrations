# Managers and leadership

**Checked:** 2026-11-14.

## Mobile-first operating view

| Need | What manager sees | Data path | AI? |
|---|---|---|---|
| New lead alert | Short summary, owner, value, CRM deep link | CRM event → automation/webhook → internal Telegram bot group/private message | No |
| Daily report | Totals, overdue items, exceptions and dashboard link | Scheduled job reads approved CRM/ERP metrics → bot sends digest | Optional summary; send only approved aggregates |
| Approve a discount or refund | Request context and Approve/Reject buttons | Button callback → backend authenticates Telegram user mapping, checks policy/current state, records audit → CRM/payment system | No; AI can classify or draft rationale, but not authorize |
| Service incident | Severity, affected service, customer case count, owner and runbook link | Grafana/monitoring → alert router → Telegram; incident service updates CRM status | Optional summary of metrics/log snippets after review |

## No-AI starting point

Scheduled reports, alert thresholds, assignment rules, and inline buttons are ordinary automation. Define a small action allowlist and verify the Telegram actor against an employee identity. A button is a request, not proof of authorization. Backend rechecks role, record status and idempotency before committing; write an audit event and return the result to the chat.

## AI option

An internal assistant can summarize selected CRM records or approved dashboard output, find a knowledge-base article, classify an incoming request, or draft a response. The model runs in the chosen AI provider or self-hosted inference service; only the selected fields/documents should be sent. Keep human confirmation for customer-facing messages, financial actions and access changes. Do not feed Telegram chat history into the model by default.

## Costs and complexity

Simple notifications are low complexity and may run through Make/Zapier/n8n or an existing Grafana alert contact point. Interactive approvals require a secured service and audit trail (medium). AI adds model usage, privacy review, evaluation and prompt-injection controls. See [catalog](product-catalog.md) for dated vendor pricing.

**Documented:** Bot API supports inline keyboards/callbacks; Grafana contact point sends alert notifications. **Proposed:** identity mapping, report query, approval policy and audit model.

Sources: [Bot API](https://core.telegram.org/bots/api), [Grafana Telegram contact point](https://grafana.com/docs/grafana/latest/alerting/configure-notifications/manage-contact-points/integrations/configure-telegram/), [MCP Telegram actions at Zapier](https://zapier.com/mcp/telegram).
