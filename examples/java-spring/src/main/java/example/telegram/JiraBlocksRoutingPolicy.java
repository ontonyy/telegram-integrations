package example.telegram;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Throwaway, process-local routing policy for synthetic Jira Blocks relations. */
public final class JiraBlocksRoutingPolicy {
    private final FakeNotificationSink sink;
    private final Map<String, NotifiedRelation> notifiedRelations = new HashMap<>();
    private final Set<String> noticedTargets = new HashSet<>();
    private final Map<String, State> lastStates = new HashMap<>();

    public JiraBlocksRoutingPolicy(FakeNotificationSink sink) {
        this.sink = sink;
    }

    /** Groups one observed batch before recording notices, so one target gets one aggregate. */
    public synchronized RouteCounts route(List<Relation> batch) {
        int notices = 0;
        int lifecycle = 0;
        int suppressed = 0;
        int duplicates = 0;
        Map<String, List<Relation>> candidatesByTarget = new LinkedHashMap<>();

        for (Relation relation : batch) {
            State previous = lastStates.get(relation.id());
            if (previous == relation.state()) {
                duplicates++;
                continue;
            }

            lastStates.put(relation.id(), relation.state());
            if (relation.state() == State.DONE_BEFORE_START) {
                suppressed++;
                continue;
            }

            if (relation.state() == State.RESOLVED || relation.state() == State.REMOVED
                    || relation.state() == State.DONE) {
                NotifiedRelation notified = notifiedRelations.get(relation.id());
                if (notified != null && previous != relation.state()) {
                    sink.recordLifecycleUpdate(notified.recipient(), notified.targetKey(), relation.id());
                    lifecycle++;
                } else {
                    suppressed++;
                }
                continue;
            }

            if (relation.state() != State.ACTIVE) {
                suppressed++;
                continue;
            }

            NotifiedRelation notified = notifiedRelations.get(relation.id());
            if (notified != null) {
                if (previous == State.RESOLVED || previous == State.REMOVED || previous == State.DONE) {
                    sink.recordLifecycleUpdate(notified.recipient(), notified.targetKey(), relation.id());
                    lifecycle++;
                } else {
                    suppressed++;
                }
                continue;
            }

            if (!eligible(relation) || noticedTargets.contains(relation.targetKey())) {
                suppressed++;
                continue;
            }
            candidatesByTarget.computeIfAbsent(relation.targetKey(), ignored -> new ArrayList<>()).add(relation);
        }

        for (List<Relation> relations : candidatesByTarget.values()) {
            relations.sort(Comparator.comparing(Relation::blockerKey));
            Relation first = relations.getFirst();
            boolean consistentRecipient = relations.stream()
                    .allMatch(relation -> relation.targetTeam() == first.targetTeam());
            if (!consistentRecipient) {
                suppressed += relations.size();
                continue;
            }
            sink.recordNotice(first.targetTeam(), first.targetKey(),
                    relations.stream().map(Relation::blockerKey).distinct().toList());
            noticedTargets.add(first.targetKey());
            relations.forEach(relation -> notifiedRelations.put(relation.id(),
                    new NotifiedRelation(first.targetKey(), first.targetTeam())));
            notices++;
        }
        return new RouteCounts(notices, lifecycle, suppressed, duplicates);
    }

    private static boolean eligible(Relation relation) {
        return relation.explicitBlocks()
                && relation.newRelation()
                && !relation.historical()
                && !relation.ambiguousEpic()
                && isKnownTeam(relation.blockerTeam())
                && isKnownTeam(relation.targetTeam())
                && relation.blockerTeam() != relation.targetTeam();
    }

    private static boolean isKnownTeam(Team team) {
        return team == Team.BE || team == Team.FE;
    }

    private record NotifiedRelation(String targetKey, Team recipient) {}

    public enum Team { BE, FE, UNKNOWN, DUAL }

    public enum State { ACTIVE, RESOLVED, REMOVED, DONE, DONE_BEFORE_START }

    public record Relation(
            String id,
            String blockerKey,
            String targetKey,
            Team blockerTeam,
            Team targetTeam,
            State state,
            String eventVersion,
            boolean explicitBlocks,
            boolean newRelation,
            boolean historical,
            boolean ambiguousEpic) {
        public Relation withState(State value) {
            return new Relation(id, blockerKey, targetKey, blockerTeam, targetTeam, value,
                    eventVersion, explicitBlocks, newRelation, historical, ambiguousEpic);
        }

        public Relation withEventVersion(String value) {
            return new Relation(id, blockerKey, targetKey, blockerTeam, targetTeam, state,
                    value, explicitBlocks, newRelation, historical, ambiguousEpic);
        }

        public Relation withTeams(Team blocker, Team target) {
            return new Relation(id, blockerKey, targetKey, blocker, target, state,
                    eventVersion, explicitBlocks, newRelation, historical, ambiguousEpic);
        }

        public Relation asHistorical() {
            return new Relation(id, blockerKey, targetKey, blockerTeam, targetTeam, state,
                    eventVersion, explicitBlocks, false, true, ambiguousEpic);
        }

        public Relation asAmbiguousEpic() {
            return new Relation(id, blockerKey, targetKey, blockerTeam, targetTeam, state,
                    eventVersion, explicitBlocks, newRelation, historical, true);
        }

        public Relation notAnExplicitBlocksRelation() {
            return new Relation(id, blockerKey, targetKey, blockerTeam, targetTeam, state,
                    eventVersion, false, newRelation, historical, ambiguousEpic);
        }
    }

    public record RouteCounts(int notices, int lifecycleUpdates, int suppressed, int duplicates) {}
}
