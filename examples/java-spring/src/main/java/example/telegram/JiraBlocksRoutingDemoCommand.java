package example.telegram;

import java.util.List;

/** Prints only scenario labels and aggregate counts for the local synthetic routing example. */
public final class JiraBlocksRoutingDemoCommand {
    private static final JiraBlocksRoutingPolicy.Team BE = JiraBlocksRoutingPolicy.Team.BE;
    private static final JiraBlocksRoutingPolicy.Team FE = JiraBlocksRoutingPolicy.Team.FE;

    private JiraBlocksRoutingDemoCommand() {}

    public static void main(String[] args) {
        var sink = new RecordingFakeNotificationSink();
        var policy = new JiraBlocksRoutingPolicy(sink);
        int notices = 0;
        int lifecycle = 0;
        int suppressed = 0;
        int duplicates = 0;

        var beToFe = relation("r1", "DEMO-BE-1", "DEMO-FE-1", BE, FE);
        var result = policy.route(List.of(beToFe));
        notices += result.notices();
        System.out.println("1. Active BE to FE: notice=" + result.notices());

        result = policy.route(List.of(relation("r2", "DEMO-FE-1", "DEMO-BE-2", FE, BE)));
        notices += result.notices();
        System.out.println("2. Active FE to BE: notice=" + result.notices());

        result = policy.route(List.of(
                relation("r3", "DEMO-BE-2", "DEMO-FE-2", BE, FE),
                relation("r4", "DEMO-BE-3", "DEMO-FE-2", BE, FE)));
        notices += result.notices();
        System.out.println("3. Two blockers, one target: aggregate notices=" + result.notices());

        result = policy.route(List.of(beToFe));
        duplicates += result.duplicates();
        System.out.println("4. Repeated relation state: duplicates=" + result.duplicates());

        result = policy.route(List.of(relation("r5", "DEMO-BE-4", "DEMO-FE-3", BE, BE)));
        suppressed += result.suppressed();
        System.out.println("5. Same team: suppressed=" + result.suppressed());

        result = policy.route(List.of(
                relation("r6", "DEMO-BE-5", "DEMO-FE-4", JiraBlocksRoutingPolicy.Team.UNKNOWN, FE),
                relation("r7", "DEMO-BE-6", "DEMO-FE-5", JiraBlocksRoutingPolicy.Team.DUAL, FE)));
        suppressed += result.suppressed();
        System.out.println("6. Unknown or dual team: suppressed=" + result.suppressed());

        result = policy.route(List.of(relation("r8", "DEMO-BE-7", "DEMO-FE-6", BE, FE).asAmbiguousEpic()));
        suppressed += result.suppressed();
        System.out.println("7. Ambiguous epic: suppressed=" + result.suppressed());

        result = policy.route(List.of(relation("r9", "DEMO-BE-8", "DEMO-FE-7", BE, FE).asHistorical()));
        suppressed += result.suppressed();
        System.out.println("8. Historical relation: suppressed=" + result.suppressed());

        result = policy.route(List.of(beToFe.withState(JiraBlocksRoutingPolicy.State.RESOLVED)
                .withEventVersion("v2")));
        lifecycle += result.lifecycleUpdates();
        System.out.println("9. Previously noticed relation resolved: lifecycle=" + result.lifecycleUpdates());

        var alreadyDone = relation("r10", "DEMO-BE-9", "DEMO-FE-8", BE, FE)
                .withState(JiraBlocksRoutingPolicy.State.DONE_BEFORE_START);
        result = policy.route(List.of(alreadyDone));
        suppressed += result.suppressed();
        System.out.println("10. Blocker already Done at demo start: notice=" + result.notices());

        System.out.println("Totals: notices=" + notices + ", lifecycle=" + lifecycle
                + ", suppressed=" + suppressed + ", duplicates=" + duplicates
                + ", fakeRecordedNotices=" + sink.notices().size()
                + ", fakeRecordedLifecycle=" + sink.lifecycleUpdates());
    }

    private static JiraBlocksRoutingPolicy.Relation relation(
            String id, String blocker, String target,
            JiraBlocksRoutingPolicy.Team blockerTeam, JiraBlocksRoutingPolicy.Team targetTeam) {
        return new JiraBlocksRoutingPolicy.Relation(id, blocker, target, blockerTeam, targetTeam,
                JiraBlocksRoutingPolicy.State.ACTIVE, "v1", true, true, false, false);
    }
}
