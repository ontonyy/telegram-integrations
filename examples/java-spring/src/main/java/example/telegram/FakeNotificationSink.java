package example.telegram;

import java.util.List;

/** Internal port for this offline example; it has no provider implementation. */
public interface FakeNotificationSink {
    void recordNotice(
            String episodeId, JiraBlocksRoutingPolicy.Team recipient, String targetKey, List<String> blockerKeys);

    void appendBlockers(String episodeId, List<String> blockerKeys);

    void recordLifecycleUpdate(
            String episodeId, JiraBlocksRoutingPolicy.Team recipient, String targetKey,
            String relationId, JiraBlocksRoutingPolicy.State state);
}
