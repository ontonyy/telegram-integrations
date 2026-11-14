# Repository workflow

- Keep work in this Telegram integrations repository unless the user explicitly asks to change another repository.
- The user requests that completed changes be committed and pushed to `origin/master` each time. Before editing, confirm the current branch is `master`; preserve unrelated changes and stage only intended paths. After the change is reviewable, run `git diff --check`, commit, and push to `origin master`.
- Do not create Telegram tickets in Jira or configure Telegram/provider credentials unless separately requested.
- Keep tokens, account sessions, phone numbers, and private customer data out of this repository.
- Label project-specific ideas as proposed until verified against the target project's current code, contracts, policy and owner decisions.

- Write repository notes, research, task seeds, and agent-facing documents in English. Communicate summaries and explanations to the user in Russian unless the user requests another language.
