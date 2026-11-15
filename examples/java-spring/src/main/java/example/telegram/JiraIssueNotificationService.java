package example.telegram;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

@Service
public class JiraIssueNotificationService {
    private static final String CREATED = "jira:issue_created";
    private static final String UPDATED = "jira:issue_updated";
    private static final String ISSUE_KEY = "^[A-Z][A-Z0-9_]{1,19}-[1-9][0-9]*$";

    private final TelegramBotClient telegram;
    private final String chatId;
    private final String projectKey;
    private final Set<String> issueTypes;
    private final Set<String> destinationStatuses;
    private final String jiraBaseUrl;

    public JiraIssueNotificationService(
            TelegramBotClient telegram,
            @Value("${jira.telegram-chat-id:}") String chatId,
            @Value("${jira.project-key:}") String projectKey,
            @Value("${jira.issue-types:}") String issueTypes,
            @Value("${jira.destination-statuses:}") String destinationStatuses,
            @Value("${jira.base-url:https://example.atlassian.net}") String jiraBaseUrl) {
        this.telegram = telegram;
        this.chatId = chatId;
        this.projectKey = projectKey.strip().toUpperCase(Locale.ROOT);
        this.issueTypes = parseSet(issueTypes);
        this.destinationStatuses = parseSet(destinationStatuses);
        URI base = URI.create(jiraBaseUrl.strip());
        if (!"https".equalsIgnoreCase(base.getScheme()) || base.getHost() == null) {
            throw new IllegalArgumentException("Jira base URL must be an HTTPS origin");
        }
        this.jiraBaseUrl = jiraBaseUrl.strip().replaceAll("/+$", "");
    }

    public void notifyIfSignificant(JsonNode payload) {
        if (chatId.isBlank() || projectKey.isBlank()) {
            return;
        }
        String event = payload.path("webhookEvent").asText("");
        JsonNode issue = payload.path("issue");
        JsonNode fields = issue.path("fields");
        String key = issue.path("key").asText("");
        String issueProject = fields.path("project").path("key").asText("").toUpperCase(Locale.ROOT);
        String issueType = fields.path("issuetype").path("name").asText("");
        String summary = fields.path("summary").asText("");
        if (!key.matches(ISSUE_KEY) || !projectKey.equals(issueProject)
                || (!issueTypes.isEmpty() && !issueTypes.contains(issueType))) {
            return;
        }

        String change = changeDescription(event, payload.path("changelog"), fields);
        if (change == null) {
            return;
        }
        String clippedSummary = summary.length() > 160 ? summary.substring(0, 157) + "..." : summary;
        String message = "<b>Jira " + escape(change) + ": " + escape(key) + "</b>\n"
                + escape(clippedSummary) + "\n<a href=\"" + jiraBaseUrl + "/browse/" + key + "\">Open issue</a>";
        telegram.sendHtmlOrLog(chatId, message);
    }

    private String changeDescription(String event, JsonNode changelog, JsonNode fields) {
        if (CREATED.equals(event)) {
            return "issue created";
        }
        if (!UPDATED.equals(event) || !changelog.path("items").isArray()) {
            return null;
        }
        for (JsonNode item : changelog.path("items")) {
            if (!"status".equals(item.path("field").asText(""))) {
                continue;
            }
            String destination = item.path("toString").asText(fields.path("status").path("name").asText(""));
            if (destination.isBlank()) {
                continue;
            }
            if (destinationStatuses.isEmpty() || destinationStatuses.contains(destination)) {
                return "status changed to " + destination;
            }
        }
        return null;
    }

    private Set<String> parseSet(String values) {
        return Arrays.stream(values.split(","))
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    private String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
