package example.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class JiraBlocksRoutingPolicyTest {
    @Test
    void routesOnlyNewUnambiguousCrossTeamRelationsAndTracksLifecycle() {
        RecordingFakeNotificationSink sink = new RecordingFakeNotificationSink();
        JiraBlocksRoutingPolicy policy = new JiraBlocksRoutingPolicy(sink);

        var beToFe = relation("r1", "DEMO-BE-1", "DEMO-FE-1", JiraBlocksRoutingPolicy.Team.BE, JiraBlocksRoutingPolicy.Team.FE);
        var feToBe = relation("r2", "DEMO-FE-2", "DEMO-BE-2", JiraBlocksRoutingPolicy.Team.FE, JiraBlocksRoutingPolicy.Team.BE);
        var first = policy.route(List.of(beToFe, feToBe));
        assertThat(first.notices()).isEqualTo(2);
        assertThat(sink.notices()).containsExactlyInAnyOrder(
                new RecordingFakeNotificationSink.Notice(JiraBlocksRoutingPolicy.Team.FE, "DEMO-FE-1", List.of("DEMO-BE-1")),
                new RecordingFakeNotificationSink.Notice(JiraBlocksRoutingPolicy.Team.BE, "DEMO-BE-2", List.of("DEMO-FE-2")));

        var aggregate = policy.route(List.of(
                relation("r3", "DEMO-BE-2", "DEMO-FE-3", JiraBlocksRoutingPolicy.Team.BE, JiraBlocksRoutingPolicy.Team.FE),
                relation("r4", "DEMO-BE-3", "DEMO-FE-3", JiraBlocksRoutingPolicy.Team.BE, JiraBlocksRoutingPolicy.Team.FE)));
        assertThat(aggregate.notices()).isEqualTo(1);
        assertThat(sink.notices()).filteredOn(n -> n.targetKey().equals("DEMO-FE-3"))
                .containsExactly(new RecordingFakeNotificationSink.Notice(
                        JiraBlocksRoutingPolicy.Team.FE, "DEMO-FE-3", List.of("DEMO-BE-2", "DEMO-BE-3")));

        assertThat(policy.route(List.of(beToFe)).duplicates()).isEqualTo(1);
        assertThat(policy.route(List.of(relation("r5", "DEMO-BE-4", "DEMO-FE-4", JiraBlocksRoutingPolicy.Team.BE, JiraBlocksRoutingPolicy.Team.FE)
                .withEventVersion(2))).notices()).isEqualTo(1);

        assertThat(policy.route(List.of(
                relation("r6", "DEMO-BE-5", "DEMO-FE-5", JiraBlocksRoutingPolicy.Team.BE, JiraBlocksRoutingPolicy.Team.FE)
                        .withTeams(JiraBlocksRoutingPolicy.Team.BE, JiraBlocksRoutingPolicy.Team.BE),
                relation("r7", "DEMO-BE-6", "DEMO-FE-6", JiraBlocksRoutingPolicy.Team.UNKNOWN, JiraBlocksRoutingPolicy.Team.FE),
                relation("r8", "DEMO-BE-7", "DEMO-FE-7", JiraBlocksRoutingPolicy.Team.DUAL, JiraBlocksRoutingPolicy.Team.FE),
                relation("r9", "DEMO-BE-8", "DEMO-FE-8", JiraBlocksRoutingPolicy.Team.BE, JiraBlocksRoutingPolicy.Team.FE).asAmbiguousEpic(),
                relation("r10", "DEMO-BE-9", "DEMO-FE-9", JiraBlocksRoutingPolicy.Team.BE, JiraBlocksRoutingPolicy.Team.FE).asHistorical(),
                relation("r11", "DEMO-BE-10", "DEMO-FE-10", JiraBlocksRoutingPolicy.Team.BE, JiraBlocksRoutingPolicy.Team.FE)
                        .withState(JiraBlocksRoutingPolicy.State.DONE_BEFORE_START),
                relation("r13", "DEMO-BE-12", "DEMO-FE-12", JiraBlocksRoutingPolicy.Team.BE, JiraBlocksRoutingPolicy.Team.FE)
                        .notAnExplicitBlocksRelation()))
                .suppressed()).isEqualTo(7);

        var resolve = beToFe.withState(JiraBlocksRoutingPolicy.State.RESOLVED).withEventVersion(2);
        assertThat(policy.route(List.of(resolve)).lifecycleUpdates()).isEqualTo(1);
        assertThat(policy.route(List.of(resolve)).duplicates()).isEqualTo(1);
        var reopen = beToFe.withState(JiraBlocksRoutingPolicy.State.ACTIVE).withEventVersion(3);
        assertThat(policy.route(List.of(reopen)).lifecycleUpdates()).isEqualTo(1);
        assertThat(sink.lifecycleRecords()).contains(new RecordingFakeNotificationSink.LifecycleUpdate(
                JiraBlocksRoutingPolicy.Team.FE, "DEMO-FE-1", "r1", JiraBlocksRoutingPolicy.State.RESOLVED));
        var resolvedAsDone = relation("r14", "DEMO-BE-13", "DEMO-FE-13",
                JiraBlocksRoutingPolicy.Team.BE, JiraBlocksRoutingPolicy.Team.FE);
        assertThat(policy.route(List.of(resolvedAsDone)).notices()).isEqualTo(1);
        assertThat(policy.route(List.of(resolvedAsDone.withState(JiraBlocksRoutingPolicy.State.DONE)
                .withEventVersion(2))).lifecycleUpdates()).isEqualTo(1);
        assertThat(sink.lifecycleUpdates()).isEqualTo(3);

        var doneBeforeStart = relation("r12", "DEMO-BE-11", "DEMO-FE-11", JiraBlocksRoutingPolicy.Team.BE, JiraBlocksRoutingPolicy.Team.FE)
                .withState(JiraBlocksRoutingPolicy.State.DONE_BEFORE_START);
        assertThat(policy.route(List.of(doneBeforeStart)).notices()).isZero();
        assertThat(policy.route(List.of(doneBeforeStart.withState(JiraBlocksRoutingPolicy.State.RESOLVED)
                .withEventVersion(2))).lifecycleUpdates()).isZero();
    }

    @Test
    void doesNotSendLifecycleUpdateForRelationThatWasNeverNotified() {
        var sink = new RecordingFakeNotificationSink();
        var policy = new JiraBlocksRoutingPolicy(sink);
        var relation = relation("unknown", "DEMO-BE-1", "DEMO-FE-1", JiraBlocksRoutingPolicy.Team.UNKNOWN, JiraBlocksRoutingPolicy.Team.FE);

        policy.route(List.of(relation));
        var resolved = relation.withTeams(JiraBlocksRoutingPolicy.Team.BE, JiraBlocksRoutingPolicy.Team.FE)
                .withState(JiraBlocksRoutingPolicy.State.RESOLVED).withEventVersion(2);

        assertThat(policy.route(List.of(resolved)).lifecycleUpdates()).isZero();
        assertThat(sink.lifecycleUpdates()).isZero();
    }

    @Test
    void addsLaterBlockerToOpenAggregateWithoutAnotherNotice() {
        var sink = new RecordingFakeNotificationSink();
        var policy = new JiraBlocksRoutingPolicy(sink);
        var first = relation("episode-a", "DEMO-BE-1", "DEMO-FE-1",
                JiraBlocksRoutingPolicy.Team.BE, JiraBlocksRoutingPolicy.Team.FE);
        var later = relation("episode-b", "DEMO-BE-2", "DEMO-FE-1",
                JiraBlocksRoutingPolicy.Team.BE, JiraBlocksRoutingPolicy.Team.FE);

        assertThat(policy.route(List.of(first)).notices()).isEqualTo(1);
        var nextBatch = policy.route(List.of(later));

        assertThat(nextBatch.notices()).isZero();
        assertThat(sink.notices()).containsExactly(new RecordingFakeNotificationSink.Notice(
                JiraBlocksRoutingPolicy.Team.FE, "DEMO-FE-1", List.of("DEMO-BE-1", "DEMO-BE-2")));
    }

    @Test
    void opensNewEpisodeAfterEveryNotifiedBlockerIsResolved() {
        var sink = new RecordingFakeNotificationSink();
        var policy = new JiraBlocksRoutingPolicy(sink);
        var first = relation("old-episode", "DEMO-BE-1", "DEMO-FE-1",
                JiraBlocksRoutingPolicy.Team.BE, JiraBlocksRoutingPolicy.Team.FE);
        policy.route(List.of(first));
        var second = relation("second-blocker", "DEMO-BE-2", "DEMO-FE-1",
                JiraBlocksRoutingPolicy.Team.BE, JiraBlocksRoutingPolicy.Team.FE);
        policy.route(List.of(second));
        policy.route(List.of(first.withState(JiraBlocksRoutingPolicy.State.RESOLVED).withEventVersion(2)));

        var third = relation("third-blocker", "DEMO-BE-3", "DEMO-FE-1",
                JiraBlocksRoutingPolicy.Team.BE, JiraBlocksRoutingPolicy.Team.FE);
        assertThat(policy.route(List.of(third)).notices()).isZero();
        assertThat(policy.route(List.of(second.withState(JiraBlocksRoutingPolicy.State.RESOLVED)
                .withEventVersion(2))).lifecycleUpdates()).isEqualTo(1);
        assertThat(policy.route(List.of(third.withState(JiraBlocksRoutingPolicy.State.RESOLVED)
                .withEventVersion(2))).lifecycleUpdates()).isEqualTo(1);

        var newRelation = relation("new-episode", "DEMO-BE-2", "DEMO-FE-1",
                JiraBlocksRoutingPolicy.Team.BE, JiraBlocksRoutingPolicy.Team.FE);

        assertThat(policy.route(List.of(newRelation)).notices()).isEqualTo(1);
        var laterInNewEpisode = relation("later-new-episode", "DEMO-BE-4", "DEMO-FE-1",
                JiraBlocksRoutingPolicy.Team.BE, JiraBlocksRoutingPolicy.Team.FE);
        assertThat(policy.route(List.of(laterInNewEpisode)).aggregatedBlockers()).isEqualTo(1);
        assertThat(sink.notices()).hasSize(2);
        assertThat(sink.notices().getLast().blockerKeys()).containsExactly("DEMO-BE-2", "DEMO-BE-4");
    }

    @Test
    void ignoresOlderVersionsAndRecordsRemovalOnlyForNotifiedRelation() {
        var sink = new RecordingFakeNotificationSink();
        var policy = new JiraBlocksRoutingPolicy(sink);
        var relation = relation("ordered", "DEMO-BE-1", "DEMO-FE-1",
                JiraBlocksRoutingPolicy.Team.BE, JiraBlocksRoutingPolicy.Team.FE);
        policy.route(List.of(relation));
        assertThat(policy.route(List.of(relation.withState(JiraBlocksRoutingPolicy.State.RESOLVED)
                .withEventVersion(3))).lifecycleUpdates()).isEqualTo(1);

        var stale = policy.route(List.of(relation.withState(JiraBlocksRoutingPolicy.State.ACTIVE)
                .withEventVersion(2)));
        assertThat(stale.lifecycleUpdates()).isZero();
        assertThat(stale.stale()).isEqualTo(1);

        var removable = relation("removed", "DEMO-BE-2", "DEMO-FE-2",
                JiraBlocksRoutingPolicy.Team.BE, JiraBlocksRoutingPolicy.Team.FE);
        policy.route(List.of(removable));
        assertThat(policy.route(List.of(removable.withState(JiraBlocksRoutingPolicy.State.REMOVED)
                .withEventVersion(2))).lifecycleUpdates()).isEqualTo(1);
        assertThat(sink.lifecycleRecords()).anyMatch(update ->
                update.relationId().equals("removed") && update.state() == JiraBlocksRoutingPolicy.State.REMOVED);
    }

    @Test
    void suppressesWholeTargetBatchWhenRecipientTeamsConflict() {
        var sink = new RecordingFakeNotificationSink();
        var policy = new JiraBlocksRoutingPolicy(sink);

        var result = policy.route(List.of(
                relation("to-fe", "DEMO-BE-1", "DEMO-TARGET-1",
                        JiraBlocksRoutingPolicy.Team.BE, JiraBlocksRoutingPolicy.Team.FE),
                relation("to-be", "DEMO-FE-1", "DEMO-TARGET-1",
                        JiraBlocksRoutingPolicy.Team.FE, JiraBlocksRoutingPolicy.Team.BE),
                relation("same-team-conflict", "DEMO-BE-2", "DEMO-TARGET-1",
                        JiraBlocksRoutingPolicy.Team.BE, JiraBlocksRoutingPolicy.Team.BE)));

        assertThat(result.notices()).isZero();
        assertThat(result.suppressed()).isEqualTo(3);
        assertThat(sink.notices()).isEmpty();
    }

    @Test
    void classificationCorrectionDoesNotBackfillAnExistingActiveRelation() {
        var sink = new RecordingFakeNotificationSink();
        var policy = new JiraBlocksRoutingPolicy(sink);
        var unknown = relation("classification", "DEMO-BE-1", "DEMO-FE-1",
                JiraBlocksRoutingPolicy.Team.UNKNOWN, JiraBlocksRoutingPolicy.Team.FE);
        policy.route(List.of(unknown));

        var corrected = unknown.withTeams(JiraBlocksRoutingPolicy.Team.BE,
                JiraBlocksRoutingPolicy.Team.FE).withEventVersion(2);

        assertThat(policy.route(List.of(corrected)).notices()).isZero();
        assertThat(policy.route(List.of(corrected)).duplicates()).isEqualTo(1);
        assertThat(policy.route(List.of(unknown.withEventVersion(1))).stale()).isEqualTo(1);
        assertThat(sink.notices()).isEmpty();
    }

    private static JiraBlocksRoutingPolicy.Relation relation(
            String id, String blocker, String target, JiraBlocksRoutingPolicy.Team blockerTeam,
            JiraBlocksRoutingPolicy.Team targetTeam) {
        return new JiraBlocksRoutingPolicy.Relation(
                id, blocker, target, blockerTeam, targetTeam,
                JiraBlocksRoutingPolicy.State.ACTIVE, 1, true, true, false, false);
    }
}
