# Telegram groups, topics and shared Mini App scenarios

**Checked:** 2026-11-14. Bot API 10.4.

## Who sees what

An ordinary bot can be added to a group. The Bot API FAQ says bots receive service messages; in groups, bots that are administrators or have privacy mode disabled receive all messages except those from other bots. With privacy mode enabled (the default), updates are limited to addressed commands, certain general commands, messages sent via the bot and replies directed to it. Bots do not receive arbitrary old group history through Bot API. A channel bot receives channel messages where it is a member; posting/moderation requires appropriate rights.

Forum topics are separate conversations inside supergroups. A bot can respond in a topic by using its `message_thread_id`; creating/editing/deleting topics requires topic management rights. Keep support, releases and incident notifications in separate topics to make the phone experience manageable.

## Choose the interaction

| Need | Telegram feature | Data boundary |
|---|---|---|
| Explicit action from group | `/task`, addressed command, reply, callback button | Bot receives the targeted update/replied-to message; not entire history |
| Vote on meeting time | Bot API poll or buttons | Votes/options, not broad private user history |
| Structured shared ordering/availability | Direct-link Mini App | `chat_type`/`chat_instance` allow a server-side shared workspace; Mini App cannot read group messages or post as the user |
| Keep a long-running team process organized | Forum topic + bot notifications | Only messages delivered under group privacy/rights settings |
| Summarize selected discussion | User replies to bot or explicitly submits selected text | Restrict the prompt to submitted content; disclose any AI processing and review Telegram terms |

Avoid disabling privacy mode unless the feature needs ambient group updates. If enabled, make the purpose visible to group members, minimize retention, and respect Bot Developer Terms. Business connected-bot recipient selection is documented for private chats; do not use it as evidence of a general business-account group reader.

## Patterns

### Turn a message into a task after explicit action

1. User replies to a message with `/task` or presses a bot button.
2. Bot gets the command and the reply context where available.
3. Backend validates group ID, allowed topic and actor role.
4. Backend creates a Jira/CRM task with a link to the originating Telegram message if the platform provides a usable link.
5. Bot posts a short confirmation in the same topic; no content is sent to AI by default.

### Choose a time together

Use native poll for a quick vote. Use a direct-link Mini App if users need availability rules, time-zone handling, a final booking, or shared edits. Key shared state by `chat_instance` plus an application-generated record; treat it as a context identifier, not a participant roster or authorization credential.

### Ask for a summary

Under privacy mode, ask users to explicitly reply with the text/message to summarize or invoke a command against a specific available reply. Do not silently collect all group traffic. A human should be able to inspect sources and approve any action based on the summary.

## Official sources

- [Bot FAQ: messages bots receive](https://core.telegram.org/bots/faq)
- [Bot API](https://core.telegram.org/bots/api)
- [Forum topics](https://core.telegram.org/api/forum)
- [Mini Apps: direct links and chat context](https://core.telegram.org/bots/webapps)
- [Business bot recipient scope](https://core.telegram.org/constructor/businessBotRecipients)
- [Bot Developer Terms](https://telegram.org/tos/bot-developers)

**Documented:** API update visibility and feature behavior in linked Telegram docs. **Proposed:** workflow architecture and retention choices above.
