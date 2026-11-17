# Repository Learning and Verification Roadmap

This roadmap summarizes the recent J10 change and gives a safe order for learning and checking the repository. It is a guide, not a claim that every check below has already been run.

## Recent work: crash-safe J10 upload cleanup

**Completed in commit `108b5fe` (`fix: recover abandoned Telegram file uploads`):**

- Active uploads hold a per-workspace operating-system file lock (a lease).
- A shared maintenance lock serializes workspace creation and cleanup across processes.
- The next eligible file intake removes abandoned workspaces only after acquiring their released lease.
- Locked workspaces, symlinks, and directories with unexpected contents are left untouched.
- Focused tests cover abandoned-workspace cleanup, a held lease, and unexpected entries. They use synthetic file contents and a fake Telegram gateway.
- The change was pushed to `origin/master`; the focused `FileIntakeServiceTest` and `git diff --check` passed.

**Known verification boundary:** the held-lease test runs in one JVM. It does not exercise two separate processes or prove locking behavior on every filesystem. No live Telegram download was performed.

## Learning and verification steps

### 1. Get the repository map

Read these first:

1. [Repository overview](README.md)
2. [Concept-first learning path](learning/README.md)
3. [Java Spring example guide](examples/java-spring/README.md)
4. [TDLib example guide](examples/tdlib-java/README.md)
5. [Completed issue guides](tasks/completed-issues/README.md)

The repository has two separate Java examples. The Spring sample is a credential-free bot/service demonstration. The TDLib sample separates fake-port tests from its optional native adapter and real-account flow.

### 2. Trace J10 end to end

Read [J10](tasks/completed-issues/J10.md), then inspect:

- `FileIntakeService` — validation, workspace lifecycle, text processing, and cleanup.
- `TelegramFileGateway` and `BotApiTelegramFileGateway` — the boundary between service logic and Telegram's file API.
- `FileIntakeServiceTest` and `FileIntakeWebhookTest` — synthetic service and webhook behavior.

Be able to explain where metadata is checked, when the download starts, what can be retained on disk, and which cleanup path runs after success or failure.

### 3. Understand and verify the lease design

The lease is an OS file lock held for the lifetime of an upload. The maintenance lock prevents another cooperating process from racing workspace setup or cleanup. Cleanup uses lock ownership, rather than file age, to decide whether a workspace may be abandoned.

Next, add or run a focused test with **two separate JVM processes** sharing a temporary root: one process holds an upload lease while the other starts an intake. Confirm the first process's directory and synthetic partial content remain intact. Then simulate process termination and confirm a later intake can remove the released workspace. Do not use Telegram credentials or real files for this check.

Before using a shared or network-mounted temporary directory, verify that the actual filesystem provides reliable cross-process Java `FileLock` semantics. If it does not, do not rely on this recovery mechanism there. The local tests do not establish this filesystem property.

### 4. Run the repository's documented Java checks

Use Java 25 as required by the sample builds.

From the repository root, run the Spring sample's CI test command:

```bash
./examples/java-spring/gradlew -p examples/java-spring clean test
```

Run the separate TDLib fake-port tests:

```bash
./examples/java-spring/gradlew -p examples/tdlib-java test
```

The CI workflow runs the Spring sample tests and may send a Telegram notification after a trusted push to `master` when its repository secrets and variables are configured. TDLib fake-port tests do not require credentials, JNI, or a Telegram connection. Building the optional native TDLib adapter is a separate environment-dependent check; follow its README only if that adapter is in scope.

### 5. Review the full repository safely

- Compare `.github/workflows/java-sample-ci.yml` with the local commands and note which checks are not in CI.
- Read each example's README before running optional flows. Do not run real Telegram login, downloads, sends, or provider integrations as part of a credential-free review.
- Review webhook authentication, input-size limits, idempotency, outbound retries, consent, data retention, and authorization boundaries in the relevant services and tests.
- Check documentation claims against tests and implementation; label mocks and fake adapters as local verification, not live connectivity.
- Inspect changed paths and run `git diff --check`. Keep credentials, account sessions, phone numbers, and customer data out of the repository and logs.

## Completion criteria

The full local review is complete when both documented Java test suites pass, the two-JVM lease test demonstrates active-workspace preservation and crash recovery, filesystem support is confirmed for the intended deployment storage, and any gaps between CI, docs, and code are recorded accurately. Live account/provider checks are separate and require their own explicit scope and credentials.
