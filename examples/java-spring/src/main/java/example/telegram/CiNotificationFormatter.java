package example.telegram;

import java.util.Map;
import java.util.Locale;

public final class CiNotificationFormatter {
    private static final Map<String, String> STATUS_LABELS = Map.of(
            "success", "Success",
            "failure", "Failure",
            "cancelled", "Cancelled");

    private CiNotificationFormatter() {}

    public static String format(String status, String repository, String ref, String commit, String runUrl) {
        String label = STATUS_LABELS.getOrDefault(safeValue(status, 32).toLowerCase(Locale.ROOT), "Unknown");
        return "<b>CI " + escapeHtml(label) + "</b>\n"
                + "<code>" + escapeHtml(safeValue(repository, 160)) + "</code>\n"
                + "Ref: <code>" + escapeHtml(safeValue(ref, 160)) + "</code>\n"
                + "Commit: <code>" + escapeHtml(shortCommit(commit)) + "</code>\n"
                + escapeHtml(safeValue(runUrl, 400));
    }

    private static String shortCommit(String value) {
        String commit = safeValue(value, 64);
        return commit.length() <= 8 ? commit : commit.substring(0, 8);
    }

    private static String safeValue(String value, int maxLength) {
        if (value == null) {
            return "unknown";
        }
        String normalized = value.replaceAll("[\\p{Cntrl}]", "?").strip();
        if (normalized.isEmpty()) {
            return "unknown";
        }
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
