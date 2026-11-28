package example.telegram;

import java.util.List;

public record JiraIssueDetails(
        String id, String key, String projectKey, List<String> labels, String statusCategory, String issueType) {}
