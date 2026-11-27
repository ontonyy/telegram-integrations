package example.telegram;

import java.util.ArrayList;
import java.util.List;

/** Records counts and synthetic references only; never sends or prints payloads. */
public final class RecordingFakeNotificationSink implements FakeNotificationSink {
    private final List<Notice> notices = new ArrayList<>();
    private final List<LifecycleUpdate> lifecycleUpdates = new ArrayList<>();

    @Override
    public synchronized void recordNotice(
            JiraBlocksRoutingPolicy.Team recipient, String targetKey, List<String> blockerKeys) {
        notices.add(new Notice(recipient, targetKey, List.copyOf(blockerKeys)));
    }

    @Override
    public synchronized void recordLifecycleUpdate(
            JiraBlocksRoutingPolicy.Team recipient, String targetKey, String relationId) {
        lifecycleUpdates.add(new LifecycleUpdate(recipient, targetKey, relationId));
    }

    public synchronized List<Notice> notices() {
        return List.copyOf(notices);
    }

    public synchronized int lifecycleUpdates() {
        return lifecycleUpdates.size();
    }

    public synchronized List<LifecycleUpdate> lifecycleRecords() {
        return List.copyOf(lifecycleUpdates);
    }

    public record Notice(JiraBlocksRoutingPolicy.Team recipient, String targetKey, List<String> blockerKeys) {}

    public record LifecycleUpdate(JiraBlocksRoutingPolicy.Team recipient, String targetKey, String relationId) {}
}
