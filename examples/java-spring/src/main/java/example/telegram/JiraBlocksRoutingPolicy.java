package example.telegram;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Throwaway, process-local routing policy for synthetic Jira Blocks relations. */
public final class JiraBlocksRoutingPolicy {
    private final JiraBlocksNotificationSink sink;
    private final Map<String, RelationProgress> relationProgress = new HashMap<>();
    private final Set<String> suppressedBeforeStart = new LinkedHashSet<>();
    private final Map<String, NotifiedRelation> notifiedRelations = new HashMap<>();
    private final Map<String, Episode> openEpisodes = new HashMap<>();
    private long nextEpisodeNumber = 1;

    public JiraBlocksRoutingPolicy(JiraBlocksNotificationSink sink) {
        this.sink = sink;
    }

    /** Routes one synthetic batch and aggregates new blockers into an open target episode. */
    public synchronized RouteCounts route(List<Relation> batch) {
        int notices = 0;
        int lifecycle = 0;
        int suppressed = 0;
        int duplicates = 0;
        int stale = 0;
        int aggregatedBlockers = 0;
        Map<String, List<Relation>> candidatesByTarget = new LinkedHashMap<>();
        Map<String, Set<Team>> targetTeamsByTarget = new HashMap<>();

        for (Relation relation : batch) {
            if (suppressedBeforeStart.contains(relation.id())) {
                suppressed++;
                continue;
            }
            RelationProgress previous = relationProgress.get(relation.id());
            if (previous != null && relation.eventVersion() < previous.version()) {
                stale++;
                continue;
            }
            if (previous != null && relation.eventVersion() == previous.version()) {
                duplicates++;
                continue;
            }
            if (previous != null && previous.state() == relation.state()) {
                relationProgress.put(relation.id(), new RelationProgress(relation.eventVersion(), relation.state()));
                duplicates++;
                continue;
            }
            relationProgress.put(relation.id(), new RelationProgress(relation.eventVersion(), relation.state()));

            if (relation.state() == State.DONE_BEFORE_START) {
                suppressedBeforeStart.add(relation.id());
                suppressed++;
                continue;
            }

            if (isTerminal(relation.state())) {
                NotifiedRelation notified = notifiedRelations.get(relation.id());
                if (notified == null) {
                    suppressed++;
                    continue;
                }
                sink.recordLifecycleUpdate(notified.episode().id, notified.recipient(),
                        notified.targetKey(), relation.id(), relation.state());
                notified.episode().activeRelationIds.remove(relation.id());
                lifecycle++;
                closeIfEmpty(notified.episode());
                continue;
            }

            if (relation.state() == State.STARTED) {
                NotifiedRelation notified = notifiedRelations.get(relation.id());
                if (notified == null) {
                    suppressed++;
                    continue;
                }
                sink.recordLifecycleUpdate(notified.episode().id, notified.recipient(),
                        notified.targetKey(), relation.id(), relation.state());
                lifecycle++;
                continue;
            }

            if (relation.state() != State.ACTIVE) {
                suppressed++;
                continue;
            }

            NotifiedRelation notified = notifiedRelations.get(relation.id());
            if (notified != null) {
                Episode episode = openEpisodes.get(notified.targetKey());
                if (episode == null) {
                    episode = notified.episode();
                    openEpisodes.put(notified.targetKey(), episode);
                }
                episode.activeRelationIds.add(relation.id());
                notifiedRelations.put(relation.id(), notified.inEpisode(episode));
                sink.recordLifecycleUpdate(episode.id, notified.recipient(),
                        notified.targetKey(), relation.id(), State.ACTIVE);
                lifecycle++;
                continue;
            }

            if (isNewExplicitRelation(relation) && isKnownTeam(relation.targetTeam())) {
                targetTeamsByTarget.computeIfAbsent(relation.targetKey(), ignored -> new LinkedHashSet<>())
                        .add(relation.targetTeam());
            }
            if (!eligible(relation)) {
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
            boolean consistentTargetClassification = targetTeamsByTarget
                    .getOrDefault(first.targetKey(), Set.of()).size() <= 1;
            Episode currentEpisode = openEpisodes.get(first.targetKey());
            boolean matchesCurrentRecipient = currentEpisode == null
                    || currentEpisode.recipient == first.targetTeam();
            if (!consistentRecipient || !consistentTargetClassification || !matchesCurrentRecipient) {
                suppressed += relations.size();
                continue;
            }

            List<String> blockerKeys = relations.stream().map(Relation::blockerKey).distinct().toList();
            Episode episode = currentEpisode;
            if (episode == null) {
                String episodeId = first.targetKey() + "/episode-" + nextEpisodeNumber++;
                episode = new Episode(episodeId, first.targetKey(), first.targetTeam());
                openEpisodes.put(first.targetKey(), episode);
                sink.recordNotice(episode.id, episode.recipient, episode.targetKey, blockerKeys);
                notices++;
            } else {
                sink.appendBlockers(episode.id, blockerKeys);
                aggregatedBlockers += blockerKeys.size();
            }

            for (Relation relation : relations) {
                episode.activeRelationIds.add(relation.id());
                notifiedRelations.put(relation.id(),
                        new NotifiedRelation(episode.targetKey, episode.recipient, episode));
            }
        }

        return new RouteCounts(notices, lifecycle, suppressed, duplicates, stale, aggregatedBlockers);
    }

    private void closeIfEmpty(Episode episode) {
        if (episode.activeRelationIds.isEmpty()) {
            openEpisodes.remove(episode.targetKey, episode);
        }
    }

    private static boolean isTerminal(State state) {
        return state == State.RESOLVED || state == State.REMOVED || state == State.DONE;
    }

    private static boolean eligible(Relation relation) {
        return isNewExplicitRelation(relation)
                && !relation.ambiguousEpic()
                && isKnownTeam(relation.blockerTeam())
                && isKnownTeam(relation.targetTeam())
                && relation.blockerTeam() != relation.targetTeam();
    }

    private static boolean isNewExplicitRelation(Relation relation) {
        return relation.explicitBlocks() && relation.newRelation() && !relation.historical();
    }

    private static boolean isKnownTeam(Team team) {
        return team == Team.BE || team == Team.FE;
    }

    private record RelationProgress(long version, State state) {}

    private record NotifiedRelation(String targetKey, Team recipient, Episode episode) {
        private NotifiedRelation inEpisode(Episode currentEpisode) {
            return new NotifiedRelation(targetKey, recipient, currentEpisode);
        }
    }

    private static final class Episode {
        private final String id;
        private final String targetKey;
        private final Team recipient;
        private final LinkedHashSet<String> activeRelationIds = new LinkedHashSet<>();

        private Episode(String id, String targetKey, Team recipient) {
            this.id = id;
            this.targetKey = targetKey;
            this.recipient = recipient;
        }
    }

    public enum Team { BE, FE, UNKNOWN, DUAL }

    public enum State { ACTIVE, STARTED, RESOLVED, REMOVED, DONE, DONE_BEFORE_START }

    public record Relation(
            String id,
            String blockerKey,
            String targetKey,
            Team blockerTeam,
            Team targetTeam,
            State state,
            long eventVersion,
            boolean explicitBlocks,
            boolean newRelation,
            boolean historical,
            boolean ambiguousEpic) {
        public Relation withState(State value) {
            return new Relation(id, blockerKey, targetKey, blockerTeam, targetTeam, value,
                    eventVersion, explicitBlocks, newRelation, historical, ambiguousEpic);
        }

        public Relation withEventVersion(long value) {
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

    public record RouteCounts(
            int notices, int lifecycleUpdates, int suppressed, int duplicates,
            int stale, int aggregatedBlockers) {}
}
