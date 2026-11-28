package example.telegram;

/** Read-only seam used by the optional Jira Blocks example. */
public interface JiraIssueReader {
    JiraIssueDetails read(String issueId);
}
