# CVN-1156 — Jira Blocks routing gap brainstorm

**Status:** gap inventory; the owner accepted the local-only policy choices on 2026-11-28. The synthetic example implements those choices; this note does not authorize live Jira or Telegram behavior.

## Frame

The question is whether the synthetic CVN-1156 router handles the lifecycle of an explicitly linked cross-team blocker without losing a new blocker, sending a stale notice, or guessing a recipient. The only direct need signal in the fact pack is `REPORTED (n=1)` for inter-team waiting in general. None of the edge cases below has its own demand evidence, and the prototype does not measure Telegram value.

These are problem statements and testable risks, not proposed product features.

## Problem candidates

Scores are 0–5. Demand scores measure evidence for that specific edge case, not the general report of inter-team waiting. Buildability estimates a local synthetic check. Moat is not meaningful for this internal example.

| Problem | Who is affected | Problem and why it surfaced now | Biggest risk | Demand | Buildability | Moat |
|---|---|---|---|---:|---:|---:|
| Later blocker disappears | The team responsible for a blocked target | Once a target has one notice, a new blocker arriving in a later `route(batch)` call is suppressed and never added to the fake's recorded aggregate. Batch boundaries now expose a gap between snapshot aggregation and event arrival. | A second notice may create noise, while suppression hides new work. | 0 | 5 | 0 |
| A resolved target never starts a fresh episode | The blocked target's team | `noticedTargets` is never cleared, so a later genuinely new blocker for the same target is suppressed for the policy instance's lifetime. The current lifecycle code makes this visible. | Clearing too early can re-alert on an old blocker. | 0 | 4 | 0 |
| Team correction cannot make a relation eligible | Teams consuming routing notices | An active relation first classified `UNKNOWN` is remembered as active; a later event with the same state but corrected team is treated as a duplicate. Team mapping changes are not represented by the dedup key. | Retrying classification can become an accidental backfill. | 0 | 4 | 0 |
| Event order can resurrect a stale blocker | The blocked target's team | A terminal event followed by a delayed active event can be treated as a new notice because `eventVersion` is unused and there is no ordering check. | Jira event ordering/version semantics are not established by this synthetic example. | 0 | 3 | 0 |
| Same-state events have no explicit meaning | The router maintainer | Dedup treats every repeated relation/state as a duplicate, while `eventVersion` is carried but ignored. The model cannot tell retry, metadata refresh, and a new same-state event apart. | Adding version semantics without a real event contract invents behavior. | 0 | 4 | 0 |
| A target has inconsistent team labels in one batch | The teams represented by that target | The implementation suppresses the entire target group if active relations disagree about the target team. This safety rule is not covered by a focused test. | Fail-closed suppression can hide a valid notice until ownership data is corrected. | 0 | 5 | 0 |
| Removal lifecycle behavior is unproven by tests | A previously notified target team | The implementation has a `REMOVED` lifecycle path, but the tests do not assert it. The policy branch exists without evidence from the focused suite. | A test may only prove the fake's mechanics, not the meaning of Jira link removal. | 0 | 5 | 0 |
| Restart forgets dedup and notice history | Teams using the demo across runs | State is process-local, so a restarted instance cannot know what it already recorded. This is an intentional limitation of a throwaway offline demo, not a live reliability claim. | Fixing it would pull the example toward persistence and production scope. | 0 | 2 | 0 |

## Highest-priority risks to settle

1. **Later blocker disappears:** it directly affects the stated aggregation rule, and the handling point is concrete.
2. **Resolved target never starts a fresh episode:** it determines whether a genuinely new relation may alert after the old episode ended.
3. **Stale or corrected events:** ordering and classification corrections can turn a safe suppression rule into either a missed notice or a false notice. The event contract is not available in this local-only scope, so this is a decision boundary, not a reason to invent a Jira adapter.

### Cheap checks for the top three

1. **Later blocker disappears**
   - Key bet: a later blocker for an already-noticed target should be visible to that target without producing a second independent alert.
   - First validation: add a focused synthetic sequence with blocker A in one batch and blocker B in a later batch, then ask the owner to judge the resulting aggregate/lifecycle trace.
   - Competitors: not applicable; this is an internal policy choice, not a product category.
2. **Resolved target never starts a fresh episode**
   - Key bet: after every known blocker is resolved or removed, a genuinely new relation should be treated as a new blocking episode.
   - First validation: add a synthetic sequence `active → resolved → new relation active` and check whether a new notice is wanted.
   - Competitors: not applicable; no external product comparison answers this policy question.
3. **Stale or corrected events**
   - Key bet: the real event source can distinguish stale delivery from a valid state or ownership change.
   - First validation: inspect an owner-approved event contract or synthetic event examples before changing ordering or classification rules; this local example has no such contract.
   - Competitors: not applicable; this depends on source semantics, not market positioning.

No competitor or moat analysis applies: the current task is a repository-local routing policy example, not a product market. The accepted decisions close only local logic gaps; live Jira, Telegram delivery, credentials, and pilot measurement remain outside scope unless separately authorized.

## Accepted local policy

- Keep scope at the offline fake-backed example.
- Append later blockers to the current fake aggregate without another notice.
- Close an episode after all noticed blockers become terminal; a new relation ID starts the next episode.
- Use a monotonic synthetic integer version per relation; ignore older versions, deduplicate equal versions, and do not backfill when only classification changes.
- Suppress the whole target aggregate when known target-team classifications conflict within one batch.
- Emit lifecycle updates only for relations previously recorded by the fake.

The version sequence is an explicit test assumption, not an observed Jira event contract. Live routing and Telegram effect remain `UNKNOWN`.
