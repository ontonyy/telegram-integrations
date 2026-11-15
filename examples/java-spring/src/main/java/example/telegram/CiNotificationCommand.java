package example.telegram;

import java.time.Duration;
import tools.jackson.databind.ObjectMapper;

public final class CiNotificationCommand {
    private CiNotificationCommand() {}

    public static void main(String[] args) {
        String repository = env("GITHUB_REPOSITORY", "local/example");
        String ref = env("GITHUB_REF_NAME", "local");
        String commit = env("GITHUB_SHA", "unknown");
        String serverUrl = env("GITHUB_SERVER_URL", "https://github.com").replaceAll("/+$", "");
        String runId = env("GITHUB_RUN_ID", "local-run");
        String runUrl = serverUrl + "/" + repository + "/actions/runs/" + runId;
        String status = env("CI_JOB_STATUS", "unknown");
        String message = CiNotificationFormatter.format(status, repository, ref, commit, runUrl);

        String token = env("TELEGRAM_BOT_TOKEN", "");
        String chatId = env("TELEGRAM_CHAT_ID", "");
        boolean sendEnabled = Boolean.parseBoolean(env("TELEGRAM_SEND_ENABLED", "false"));
        TelegramBotClient telegram = new TelegramBotClient(
                token,
                sendEnabled,
                env("TELEGRAM_API_BASE_URL", "https://api.telegram.org"),
                Duration.parse(env("TELEGRAM_REQUEST_TIMEOUT", "PT10S")),
                new ObjectMapper(),
                new JdkHttpTransport());

        if (chatId.isBlank()) {
            if (sendEnabled) {
                throw new IllegalStateException("TELEGRAM_CHAT_ID is required when sends are enabled");
            }
            chatId = "dry-run";
        }
        telegram.sendHtmlOrLog(chatId, message);
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null ? fallback : value;
    }
}
