package example.telegram;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Records counts and synthetic references only; never sends or prints payloads. */
public final class RecordingFakeNotificationSink implements FakeNotificationSink {
    private final List<RecordedNotice> notices = new ArrayList<>();
    private final Map<String, RecordedNotice> noticesByEpisode = new HashMap<>();
    private final List<LifecycleUpdate> lifecycleUpdates = new ArrayList<>();

    @Override
    public synchronized void recordNotice(
            String episodeId, JiraBlocksRoutingPolicy.Team recipient, String targetKey, List<String> blockerKeys) {
        var notice = new RecordedNotice(recipient, targetKey, blockerKeys);
        notices.add(notice);
        noticesByEpisode.put(episodeId, notice);
    }

    @Override
    public synchronized void appendBlockers(String episodeId, List<String> blockerKeys) {
        var notice = noticesByEpisode.get(episodeId);
        if (notice != null) {
            notice.blockerKeys.addAll(blockerKeys);
        }
    }

    @Override
    public synchronized void recordLifecycleUpdate(
            String episodeId, JiraBlocksRoutingPolicy.Team recipient, String targetKey,
            String relationId, JiraBlocksRoutingPolicy.State state) {
        lifecycleUpdates.add(new LifecycleUpdate(recipient, targetKey, relationId, state));
    }

    public synchronized List<Notice> notices() {
        return notices.stream().map(notice -> new Notice(
                notice.recipient, notice.targetKey, List.copyOf(notice.blockerKeys))).toList();
    }

    public synchronized int lifecycleUpdates() {
        return lifecycleUpdates.size();
    }

    public synchronized List<LifecycleUpdate> lifecycleRecords() {
        return List.copyOf(lifecycleUpdates);
    }

    public record Notice(JiraBlocksRoutingPolicy.Team recipient, String targetKey, List<String> blockerKeys) {}

    public record LifecycleUpdate(
            JiraBlocksRoutingPolicy.Team recipient, String targetKey, String relationId,
            JiraBlocksRoutingPolicy.State state) {}

    private static final class RecordedNotice {
        private final JiraBlocksRoutingPolicy.Team recipient;
        private final String targetKey;
        private final LinkedHashSet<String> blockerKeys;

        private RecordedNotice(
                JiraBlocksRoutingPolicy.Team recipient, String targetKey, List<String> blockerKeys) {
            this.recipient = recipient;
            this.targetKey = targetKey;
            this.blockerKeys = new LinkedHashSet<>(blockerKeys);
        }
    }
}
