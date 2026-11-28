package example.telegram;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/** Process-local adapter for explicit Jira Blocks link and blocker status webhooks. */
@Service
public class JiraBlocksWebhookService {
    private final boolean enabled;
    private final String projectKey;
    private final JiraIssueReader issues;
    private final JiraBlocksRoutingPolicy policy;
    private final long startedAtMillis;
    private final Map<String, JiraBlocksRoutingPolicy.Relation> known = new HashMap<>();
    private final Map<String, String> blockerIdByRelation = new HashMap<>();
    private final Map<String, String> blockerStatusById = new HashMap<>();

    public JiraBlocksWebhookService(
            @Value("${jira.blocks.enabled:false}") boolean enabled,
            @Value("${jira.blocks.project-key:}") String projectKey,
            JiraIssueReader issues,
            TelegramBotClient telegram,
            @Value("${jira.blocks.be-chat-id:}") String beChat,
            @Value("${jira.blocks.fe-chat-id:}") String feChat) {
        this(enabled, projectKey, issues, new JiraBlocksRoutingPolicy(
                new JiraBlocksTelegramNotificationSink(telegram, beChat, feChat)), System.currentTimeMillis());
    }

    JiraBlocksWebhookService(boolean enabled, String projectKey, JiraIssueReader issues, JiraBlocksRoutingPolicy policy) {
        this(enabled, projectKey, issues, policy, 0);
    }

    JiraBlocksWebhookService(boolean enabled, String projectKey, JiraIssueReader issues,
            JiraBlocksRoutingPolicy policy, long startedAtMillis) {
        this.enabled = enabled;
        this.projectKey = projectKey.strip().toUpperCase(Locale.ROOT);
        this.issues = issues;
        this.policy = policy;
        this.startedAtMillis = startedAtMillis;
    }

    public synchronized void process(JsonNode payload) {
        if (!enabled) return;
        String event = payload.path("webhookEvent").asText("");
        long version = payload.path("timestamp").asLong(0);
        if ("issuelink_created".equals(event)) create(payload.path("issueLink"), version);
        else if ("issuelink_deleted".equals(event)) remove(payload.path("issueLink"), version);
        else if ("jira:issue_updated".equals(event)) update(payload, version);
    }

    private void create(JsonNode link, long version) {
        if (version < startedAtMillis) return;
        JsonNode type = link.path("issueLinkType");
        if (!"Blocks".equalsIgnoreCase(type.path("name").asText(""))
                || !"blocks".equalsIgnoreCase(type.path("outwardName").asText(""))) return;
        String id = link.path("id").asText("");
        String blockerId = link.path("sourceIssueId").asText("");
        String targetId = link.path("destinationIssueId").asText("");
        if (id.isBlank() || blockerId.isBlank() || targetId.isBlank()) return;
        JiraIssueDetails blocker = issues.read(blockerId);
        JiraIssueDetails target = issues.read(targetId);
        JiraBlocksRoutingPolicy.Team blockerTeam = team(blocker);
        JiraBlocksRoutingPolicy.Team targetTeam = team(target);
        boolean ambiguousEpic = "Epic".equalsIgnoreCase(blocker.issueType())
                || "Epic".equalsIgnoreCase(target.issueType());
        JiraBlocksRoutingPolicy.State state = "done".equalsIgnoreCase(blocker.statusCategory())
                ? JiraBlocksRoutingPolicy.State.DONE_BEFORE_START : JiraBlocksRoutingPolicy.State.ACTIVE;
        JiraBlocksRoutingPolicy.Relation relation = new JiraBlocksRoutingPolicy.Relation(
                id, blocker.key(), target.key(), blockerTeam, targetTeam, state, version,
                true, true, false, ambiguousEpic);
        known.put(id, relation);
        blockerIdByRelation.put(id, blockerId);
        blockerStatusById.put(blockerId, blocker.statusCategory());
        policy.route(List.of(relation));
    }

    private void remove(JsonNode link, long version) {
        String id = link.path("id").asText("");
        JiraBlocksRoutingPolicy.Relation previous = known.get(id);
        if (previous != null) policy.route(List.of(previous.withState(JiraBlocksRoutingPolicy.State.REMOVED)
                .withEventVersion(version)));
    }

    private void update(JsonNode payload, long version) {
        boolean statusChanged = false;
        for (JsonNode item : payload.path("changelog").path("items")) {
            if ("status".equalsIgnoreCase(item.path("field").asText(""))) statusChanged = true;
        }
        if (!statusChanged) return;
        String issueId = payload.path("issue").path("id").asText("");
        String category = payload.path("issue").path("fields").path("status")
                .path("statusCategory").path("key").asText("");
        String previousCategory = blockerStatusById.put(issueId, category);
        if (previousCategory == null || previousCategory.equalsIgnoreCase(category)) return;
        JiraBlocksRoutingPolicy.State state;
        if ("done".equalsIgnoreCase(category) && !"done".equalsIgnoreCase(previousCategory)) {
            state = JiraBlocksRoutingPolicy.State.DONE;
        } else if ("done".equalsIgnoreCase(previousCategory) && !"done".equalsIgnoreCase(category)) {
            state = JiraBlocksRoutingPolicy.State.ACTIVE;
        } else if ("new".equalsIgnoreCase(previousCategory) && "indeterminate".equalsIgnoreCase(category)) {
            state = JiraBlocksRoutingPolicy.State.STARTED;
        } else {
            return;
        }
        for (var entry : known.entrySet()) {
            JiraBlocksRoutingPolicy.Relation relation = entry.getValue();
            if (issueId.equals(blockerIdByRelation.get(entry.getKey()))) {
                policy.route(List.of(relation.withState(state).withEventVersion(version)));
                known.put(entry.getKey(), relation.withState(state).withEventVersion(version));
            }
        }
    }

    private JiraBlocksRoutingPolicy.Team team(JiraIssueDetails issue) {
        if (issue == null || projectKey.isBlank() || !projectKey.equalsIgnoreCase(issue.projectKey())) {
            return JiraBlocksRoutingPolicy.Team.UNKNOWN;
        }
        boolean be = issue.labels().stream().anyMatch(label -> "be".equalsIgnoreCase(label));
        boolean fe = issue.labels().stream().anyMatch(label -> "fe".equalsIgnoreCase(label));
        if (be && fe) return JiraBlocksRoutingPolicy.Team.DUAL;
        if (be) return JiraBlocksRoutingPolicy.Team.BE;
        if (fe) return JiraBlocksRoutingPolicy.Team.FE;
        return JiraBlocksRoutingPolicy.Team.UNKNOWN;
    }
}
