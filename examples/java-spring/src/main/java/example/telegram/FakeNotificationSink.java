package example.telegram;

/** Internal port for this offline example; it has no provider implementation. */
public interface FakeNotificationSink {
    void recordNotice(JiraBlocksRoutingPolicy.Team recipient, String targetKey, java.util.List<String> blockerKeys);

    void recordLifecycleUpdate(JiraBlocksRoutingPolicy.Team recipient, String targetKey, String relationId);
}
