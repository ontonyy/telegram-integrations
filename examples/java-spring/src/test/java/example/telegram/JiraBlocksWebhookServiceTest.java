package example.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class JiraBlocksWebhookServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void routesOnlyBlocksFromTheOutwardBlockerToTheBlockedTeamsChat() throws Exception {
        var source = issue("101", "DEMO-BE-1", List.of("be"), "new", "Task");
        var target = issue("202", "DEMO-FE-1", List.of("fe"), "new", "Task");
        var reader = new FakeJiraIssueReader(Map.of("101", source, "202", target));
        var sink = new RecordingFakeNotificationSink();
        var service = service(reader, sink);

        service.process(linkEvent("issuelink_created", "link-1", 100, "Blocks", "blocks",
                "101", "202"));
        service.process(linkEvent("issuelink_created", "link-2", 101, "Relates", "relates to",
                "101", "202"));

        assertThat(sink.notices()).containsExactly(new RecordingFakeNotificationSink.Notice(
                JiraBlocksRoutingPolicy.Team.FE, "DEMO-FE-1", List.of("DEMO-BE-1")));
        assertThat(reader.calls()).isEqualTo(2);
    }

    @Test
    void routesFeBlockerToBeTarget() throws Exception {
        var reader = new FakeJiraIssueReader(Map.of(
                "101", issue("101", "DEMO-FE-1", List.of("fe"), "new", "Task"),
                "202", issue("202", "DEMO-BE-1", List.of("be"), "new", "Task")));
        var sink = new RecordingFakeNotificationSink();

        service(reader, sink).process(linkEvent("issuelink_created", "link-fe-be", 100,
                "Blocks", "blocks", "101", "202"));

        assertThat(sink.notices()).containsExactly(new RecordingFakeNotificationSink.Notice(
                JiraBlocksRoutingPolicy.Team.BE, "DEMO-BE-1", List.of("DEMO-FE-1")));
    }

    @Test
    void suppressesAmbiguousTeamsAndBlockersDoneBeforeLinkCreation() throws Exception {
        var reader = new FakeJiraIssueReader(Map.of(
                "101", issue("101", "DEMO-BE-1", List.of("be", "fe"), "new", "Task"),
                "202", issue("202", "DEMO-FE-1", List.of("fe"), "new", "Task"),
                "303", issue("303", "DEMO-BE-2", List.of("be"), "done", "Task"),
                "404", issue("404", "DEMO-FE-2", List.of("fe"), "new", "Task")));
        var sink = new RecordingFakeNotificationSink();
        var service = service(reader, sink);

        service.process(linkEvent("issuelink_created", "dual", 100, "Blocks", "blocks",
                "101", "202"));
        service.process(linkEvent("issuelink_created", "already-done", 101, "Blocks", "blocks",
                "303", "404"));

        assertThat(sink.notices()).isEmpty();
    }

    @Test
    void sendsStartedDoneReopenAndRemovedLifecycleOnlyForNotifiedLinks() throws Exception {
        var reader = new FakeJiraIssueReader(Map.of(
                "101", issue("101", "DEMO-BE-1", List.of("be"), "new", "Task"),
                "202", issue("202", "DEMO-FE-1", List.of("fe"), "new", "Task")));
        var sink = new RecordingFakeNotificationSink();
        var service = service(reader, sink);
        service.process(linkEvent("issuelink_created", "link-1", 100, "Blocks", "blocks",
                "101", "202"));

        service.process(issueUpdate("101", 200, "indeterminate"));
        service.process(issueUpdate("101", 300, "done"));
        service.process(issueUpdate("101", 400, "new"));
        service.process(linkEvent("issuelink_deleted", "link-1", 500, "Blocks", "blocks",
                "101", "202"));

        assertThat(sink.lifecycleRecords()).extracting(RecordingFakeNotificationSink.LifecycleUpdate::state)
                .containsExactly(
                        JiraBlocksRoutingPolicy.State.STARTED,
                        JiraBlocksRoutingPolicy.State.DONE,
                        JiraBlocksRoutingPolicy.State.ACTIVE,
                        JiraBlocksRoutingPolicy.State.REMOVED);
    }

    @Test
    void disabledFeatureDoesNotReadJiraOrRouteEvents() throws Exception {
        var reader = new FakeJiraIssueReader(Map.of());
        var sink = new RecordingFakeNotificationSink();
        var service = new JiraBlocksWebhookService(false, "DEMO", reader,
                new JiraBlocksRoutingPolicy(sink));

        service.process(linkEvent("issuelink_created", "link-1", 100, "Blocks", "blocks",
                "101", "202"));

        assertThat(reader.calls()).isZero();
        assertThat(sink.notices()).isEmpty();
    }

    @Test
    void doesNotBackfillLinkEventsOlderThanTheProcessStartBoundary() throws Exception {
        var reader = new FakeJiraIssueReader(Map.of(
                "101", issue("101", "DEMO-BE-1", List.of("be"), "new", "Task"),
                "202", issue("202", "DEMO-FE-1", List.of("fe"), "new", "Task")));
        var sink = new RecordingFakeNotificationSink();
        var service = new JiraBlocksWebhookService(true, "DEMO", reader,
                new JiraBlocksRoutingPolicy(sink), 200);

        service.process(linkEvent("issuelink_created", "old-link", 100, "Blocks", "blocks", "101", "202"));

        assertThat(reader.calls()).isZero();
        assertThat(sink.notices()).isEmpty();
    }

    private JiraBlocksWebhookService service(FakeJiraIssueReader reader, RecordingFakeNotificationSink sink) {
        return new JiraBlocksWebhookService(true, "DEMO", reader, new JiraBlocksRoutingPolicy(sink));
    }

    private JiraIssueDetails issue(String id, String key, List<String> labels, String category, String issueType) {
        return new JiraIssueDetails(id, key, "DEMO", labels, category, issueType);
    }

    private tools.jackson.databind.JsonNode linkEvent(
            String event, String relationId, long timestamp, String type, String outwardName,
            String blockerId, String targetId) throws Exception {
        return mapper.readTree("""
                {"webhookEvent":"%s","timestamp":%d,"issueLink":{
                  "id":"%s","sourceIssueId":"%s","destinationIssueId":"%s",
                  "issueLinkType":{"name":"%s","outwardName":"%s"}}}
                """.formatted(event, timestamp, relationId, blockerId, targetId, type, outwardName));
    }

    private tools.jackson.databind.JsonNode issueUpdate(String issueId, long timestamp, String category)
            throws Exception {
        return mapper.readTree("""
                {"webhookEvent":"jira:issue_updated","timestamp":%d,
                 "issue":{"id":"%s","fields":{"status":{"statusCategory":{"key":"%s"}}}},
                 "changelog":{"items":[{"field":"status"}]}}
                """.formatted(timestamp, issueId, category));
    }

    private static final class FakeJiraIssueReader implements JiraIssueReader {
        private final Map<String, JiraIssueDetails> issues;
        private int calls;

        private FakeJiraIssueReader(Map<String, JiraIssueDetails> issues) {
            this.issues = new HashMap<>(issues);
        }

        @Override
        public JiraIssueDetails read(String issueId) {
            calls++;
            return issues.get(issueId);
        }

        private int calls() {
            return calls;
        }
    }
}
