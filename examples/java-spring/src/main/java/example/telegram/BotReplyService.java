package example.telegram;

import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class BotReplyService {
    private final Set<String> disclosedUsers = ConcurrentHashMap.newKeySet();
    private final Set<String> consentedUsers = ConcurrentHashMap.newKeySet();
    private final TelegramBotClient telegram;
    private final ClaudeClient claude;

    public BotReplyService(TelegramBotClient telegram, ClaudeClient claude) {
        this.telegram = telegram;
        this.claude = claude;
    }

    public Optional<String> handle(String rawText, String chatType, String chatId, String userId) {
        String command = rawText.strip();
        if (command.equals("/start")) {
            disclosedUsers.add(userId);
            return Optional.of("Demo assistant. /help lists commands. /ask sends only your submitted question to the configured AI provider after you use /consent. Use /stop to revoke consent.");
        }
        if (command.equals("/help")) {
            return Optional.of("Commands: /start, /help, /status, /consent, /stop, /ask <question>. AI is optional; no chat history is collected.");
        }
        if (command.equals("/status")) {
            return Optional.of("Demo status: all sample components are running.");
        }
        if (command.equals("/consent")) {
            if (!"private".equals(chatType)) {
                return Optional.of("Consent and AI requests are available only in a private chat with this bot.");
            }
            if (!disclosedUsers.contains(userId)) {
                return Optional.of("Send /start first to read how submitted questions are processed.");
            }
            consentedUsers.add(userId);
            return Optional.of("Consent recorded in volatile demo memory. A later /ask question you submit will be sent to the configured AI provider. Use /stop to revoke.");
        }
        if (command.equals("/stop")) {
            consentedUsers.remove(userId);
            return Optional.of("AI consent cleared.");
        }
        if (command.startsWith("/ask ")) {
            if (!"private".equals(chatType)) {
                return Optional.of("For privacy, /ask is disabled in groups. Open a private chat with the bot.");
            }
            if (!consentedUsers.contains(userId)) {
                return Optional.of("First read the disclosure in /start, then send /consent if you agree.");
            }
            String question = command.substring("/ask ".length()).strip();
            if (question.isBlank() || question.length() > 4_000) {
                return Optional.of("Please send a question of up to 4000 characters.");
            }
            return Optional.of(claude.answer(question));
        }
        return Optional.empty();
    }

    public void sendOrLog(String chatId, String text) {
        telegram.sendOrLog(chatId, text);
    }

    public void sendVotePrompt(
            String chatId, String text, List<List<TelegramBotClient.InlineButton>> keyboard) {
        telegram.sendMessageWithInlineKeyboardOrLog(chatId, text, keyboard);
    }

    public void sendAlert(
            String chatId, String text, List<List<TelegramBotClient.InlineButton>> keyboard) {
        telegram.sendMessageWithInlineKeyboardOrLog(chatId, text, keyboard);
    }

    public void editVoteMessage(
            String chatId, long messageId, String text, List<List<TelegramBotClient.InlineButton>> keyboard) {
        telegram.editMessageWithInlineKeyboardOrLog(chatId, messageId, text, keyboard);
    }

    public void answerCallback(String callbackId, String text) {
        telegram.answerCallbackQueryOrLog(callbackId, text);
    }
}
