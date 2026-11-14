# Marketing and communities

**Checked:** 2026-11-14.

## Channels, groups, polls and opt-in messages

Channels are suitable for announcements to subscribers; groups enable discussion; polls collect structured feedback; bots support an explicit opt-in flow, preference capture and service notifications. A bot cannot initiate a private conversation with a user who has not started/contacted it. Channel subscription is not permission for a bot to DM each subscriber.

Use Telegram channel/group capabilities for content publication and community feedback, then link campaign actions to a landing page, Mini App, CRM, or analytics system with transparent notice. Make the unsubscribe/preference control easy to find. Do not scrape usernames or import contacts into unsolicited campaigns.

## Broadcast rate caution

Telegram documents a free broadcast rate around 30 messages/second and paid broadcasts above that with a Stars charge (0.1 Star per message above 30/s). However, official pages found on the check date disagree on eligibility: the Bot API page says a 10,000-Star threshold, while FAQ/developer terms say 100,000 Stars and 100,000 monthly active users. Verify eligibility inside the account and current terms before committing to high-volume messaging; do not budget based on the lower threshold alone.

## Tools and flow

- Bot API + scheduled publishing service for opt-in notifications.
- Make/Zapier/n8n to route a form/CRM event to a bot or to publish through supported actions.
- A social publishing tool may manage a channel only if it explicitly documents Telegram channel support and access mode; verify before buying.
- Analytics should use campaign links and aggregate channel signals; do not assume private subscriber identities are exposed.

## AI in marketing

AI may draft copy or classify voluntary feedback before a human publishes/replies. For user-submitted feedback, disclose processing purpose and seek scoped consent where required. Avoid training or improving a model on Telegram platform content without satisfying Telegram's strict content licensing terms and all applicable privacy obligations.

**Documented:** bot opt-in and rate limits are in official FAQ/terms; channels and polls are Bot API methods. **Proposed:** campaign calendar, consent log, segmentation and analytics connection.

Sources: [Bot FAQ](https://core.telegram.org/bots/faq), [Bot API](https://core.telegram.org/bots/api), [Bot Developer Terms](https://telegram.org/tos/bot-developers), [Content Licensing terms](https://telegram.org/tos/content-licensing), [Bot API changelog](https://core.telegram.org/bots/api-changelog).
