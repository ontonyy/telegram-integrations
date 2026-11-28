package example.telegram;

import java.util.List;

/** Internal delivery port for the Jira Blocks example. */
public interface JiraBlocksNotificationSink {
    void recordNotice(String episodeId, JiraBlocksRoutingPolicy.Team recipient, String targetKey, List<String> blockerKeys);

    void appendBlockers(String episodeId, List<String> blockerKeys);

    void recordLifecycleUpdate(
            String episodeId, JiraBlocksRoutingPolicy.Team recipient, String targetKey,
            String relationId, JiraBlocksRoutingPolicy.State state);
}
