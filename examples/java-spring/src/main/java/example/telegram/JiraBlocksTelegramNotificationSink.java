package example.telegram;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Optional Telegram adapter. Delivery still requires both the Blocks flag and global send flag. */
final class JiraBlocksTelegramNotificationSink implements JiraBlocksNotificationSink {
    private final TelegramBotClient telegram;
    private final Map<JiraBlocksRoutingPolicy.Team, String> chats;
    private final Map<String, Long> messageIds = new HashMap<>();
    private final Map<String, List<String>> blockersByEpisode = new HashMap<>();
    private final Map<String, JiraBlocksRoutingPolicy.Team> recipientByEpisode = new HashMap<>();
    private final Map<String, String> targetByEpisode = new HashMap<>();

    JiraBlocksTelegramNotificationSink(TelegramBotClient telegram, String beChat, String feChat) {
        this.telegram = telegram;
        this.chats = Map.of(JiraBlocksRoutingPolicy.Team.BE, beChat, JiraBlocksRoutingPolicy.Team.FE, feChat);
    }

    @Override
    public synchronized void recordNotice(
            String episodeId, JiraBlocksRoutingPolicy.Team recipient, String targetKey, List<String> blockerKeys) {
        blockersByEpisode.put(episodeId, List.copyOf(blockerKeys));
        recipientByEpisode.put(episodeId, recipient);
        targetByEpisode.put(episodeId, targetKey);
        String chat = chats.get(recipient);
        if (chat == null || chat.isBlank()) return;
        long messageId = telegram.sendHtmlAndGetMessageId(chat, notice(targetKey, blockerKeys));
        if (messageId > 0) messageIds.put(episodeId, messageId);
    }

    @Override
    public synchronized void appendBlockers(String episodeId, List<String> blockerKeys) {
        List<String> current = new java.util.ArrayList<>(blockersByEpisode.getOrDefault(episodeId, List.of()));
        current.addAll(blockerKeys);
        blockersByEpisode.put(episodeId, List.copyOf(current));
        Long messageId = messageIds.get(episodeId);
        if (messageId == null) return;
        JiraBlocksRoutingPolicy.Team recipient = recipientByEpisode.get(episodeId);
        String chat = chats.get(recipient);
        if (chat != null && !chat.isBlank()) telegram.editHtmlOrLog(chat, messageId,
                notice(targetByEpisode.get(episodeId), current));
    }

    @Override
    public synchronized void recordLifecycleUpdate(
            String episodeId, JiraBlocksRoutingPolicy.Team recipient, String targetKey,
            String relationId, JiraBlocksRoutingPolicy.State state) {
        Long messageId = messageIds.get(episodeId);
        String chat = chats.get(recipient);
        if (messageId == null || chat == null || chat.isBlank()) return;
        telegram.editHtmlOrLog(chat, messageId, notice(targetKey, blockersByEpisode.getOrDefault(episodeId, List.of()))
                + "\n<b>Lifecycle:</b> " + escape(state.name().toLowerCase(java.util.Locale.ROOT)));
    }

    private String notice(String target, List<String> blockers) {
        return "<b>Jira Blocks demo</b>\nTarget: " + escape(target)
                + "\nBlocked by: " + blockers.stream().map(this::escape).collect(java.util.stream.Collectors.joining(", "));
    }

    private String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
