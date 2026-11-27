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
                .withEventVersion("v2"))).notices()).isEqualTo(1);

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

        var resolve = beToFe.withState(JiraBlocksRoutingPolicy.State.RESOLVED).withEventVersion("v2");
        assertThat(policy.route(List.of(resolve)).lifecycleUpdates()).isEqualTo(1);
        assertThat(policy.route(List.of(resolve)).duplicates()).isEqualTo(1);
        var reopen = beToFe.withState(JiraBlocksRoutingPolicy.State.ACTIVE).withEventVersion("v3");
        assertThat(policy.route(List.of(reopen)).lifecycleUpdates()).isEqualTo(1);
        assertThat(sink.lifecycleRecords()).contains(new RecordingFakeNotificationSink.LifecycleUpdate(
                JiraBlocksRoutingPolicy.Team.FE, "DEMO-FE-1", "r1"));
        var resolvedAsDone = relation("r14", "DEMO-BE-13", "DEMO-FE-13",
                JiraBlocksRoutingPolicy.Team.BE, JiraBlocksRoutingPolicy.Team.FE);
        assertThat(policy.route(List.of(resolvedAsDone)).notices()).isEqualTo(1);
        assertThat(policy.route(List.of(resolvedAsDone.withState(JiraBlocksRoutingPolicy.State.DONE)
                .withEventVersion("v2"))).lifecycleUpdates()).isEqualTo(1);
        assertThat(sink.lifecycleUpdates()).isEqualTo(3);

        var doneBeforeStart = relation("r12", "DEMO-BE-11", "DEMO-FE-11", JiraBlocksRoutingPolicy.Team.BE, JiraBlocksRoutingPolicy.Team.FE)
                .withState(JiraBlocksRoutingPolicy.State.DONE_BEFORE_START);
        assertThat(policy.route(List.of(doneBeforeStart)).notices()).isZero();
        assertThat(policy.route(List.of(doneBeforeStart.withState(JiraBlocksRoutingPolicy.State.RESOLVED)
                .withEventVersion("v2"))).lifecycleUpdates()).isZero();
    }

    @Test
    void doesNotSendLifecycleUpdateForRelationThatWasNeverNotified() {
        var sink = new RecordingFakeNotificationSink();
        var policy = new JiraBlocksRoutingPolicy(sink);
        var relation = relation("unknown", "DEMO-BE-1", "DEMO-FE-1", JiraBlocksRoutingPolicy.Team.UNKNOWN, JiraBlocksRoutingPolicy.Team.FE);

        policy.route(List.of(relation));
        var resolved = relation.withTeams(JiraBlocksRoutingPolicy.Team.BE, JiraBlocksRoutingPolicy.Team.FE)
                .withState(JiraBlocksRoutingPolicy.State.RESOLVED).withEventVersion("v2");

        assertThat(policy.route(List.of(resolved)).lifecycleUpdates()).isZero();
        assertThat(sink.lifecycleUpdates()).isZero();
    }

    private static JiraBlocksRoutingPolicy.Relation relation(
            String id, String blocker, String target, JiraBlocksRoutingPolicy.Team blockerTeam,
            JiraBlocksRoutingPolicy.Team targetTeam) {
        return new JiraBlocksRoutingPolicy.Relation(
                id, blocker, target, blockerTeam, targetTeam,
                JiraBlocksRoutingPolicy.State.ACTIVE, "v1", true, true, false, false);
    }
}
