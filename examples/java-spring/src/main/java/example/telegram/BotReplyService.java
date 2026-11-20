package example.telegram;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class BotReplyService {
    private static final int MAX_PENDING_AI_QUESTIONS = 1_000;
    private final Set<String> disclosedAiUsers = ConcurrentHashMap.newKeySet();
    private final Set<String> consentedUsers = ConcurrentHashMap.newKeySet();
    private final Map<String, String> pendingAiQuestions = new ConcurrentHashMap<>();
    private final Set<String> humanHandoffUsers = ConcurrentHashMap.newKeySet();
    private final Object aiRequestLock = new Object();
    private final Set<String> disclosedFileUsers = ConcurrentHashMap.newKeySet();
    private final Set<String> fileConsentedUsers = ConcurrentHashMap.newKeySet();
    private final TelegramBotClient telegram;
    private final ClaudeClient claude;

    public BotReplyService(TelegramBotClient telegram, ClaudeClient claude) {
        this.telegram = telegram;
        this.claude = claude;
    }

    public Optional<String> handle(String rawText, String chatType, String chatId, String userId) {
        String command = rawText.strip();
        if (command.equals("/start")) {
            return Optional.of("Demo assistant. Send /ai-help to read how the optional AI feature handles one question at a time.");
        }
        if (command.equals("/help")) {
            return Optional.of("Commands: /start, /help, /status, /ai-help, /consent, /ask <question>, /ai-confirm, /ai-cancel, /stop, /human, /file-help, /file-consent, /file-stop, /buy-demo, /refund-demo <order-reference>, /paysupport. Stars commands run only a synthetic in-memory simulation; they do not create invoices or move Stars.");
        }
        if (command.equals("/status")) {
            return Optional.of("Demo status: all sample components are running.");
        }
        if (command.equals("/ai-help")) {
            if (!"private".equals(chatType)) {
                return Optional.of("AI disclosure and requests are available only in a private chat with this bot.");
            }
            disclosedAiUsers.add(userId);
            return Optional.of("AI disclosure: only the exact text you submit with /ask is held in this app's volatile memory until you confirm, cancel, revoke consent, or restart the app. It is not sent to a provider until you confirm that specific question with /ai-confirm. On confirmation, that text is sent to the configured Anthropic API (Claude) to generate an answer; no chat history, other users' messages, files, or Business chats are added. Anthropic says API inputs and outputs are normally deleted from its backend within 30 days, with exceptions for legal requirements, policy enforcement, or a different agreement. This app does not persist prompts or answers. Use /ai-cancel before sending or /stop to revoke consent and clear a pending question. Provider terms and retention may change; review them before deployment: https://privacy.anthropic.com/en/articles/7996866-how-long-do-you-store-my-organization-s-data . Send /consent only if you agree to the disclosed processing. A separate /ai-confirm is still required for every question.");
        }
        if (command.equals("/consent")) {
            if (!"private".equals(chatType)) {
                return Optional.of("Consent and AI requests are available only in a private chat with this bot.");
            }
            if (!disclosedAiUsers.contains(userId)) {
                return Optional.of("Send /ai-help first to read the provider, purpose and retention disclosure.");
            }
            synchronized (aiRequestLock) {
                consentedUsers.add(userId);
            }
            return Optional.of("AI feature consent recorded in volatile demo memory. Each /ask question will wait for a separate /ai-confirm before it is sent to Anthropic. Use /stop to revoke consent and clear any pending question.");
        }
        if (command.equals("/ai-cancel")) {
            if (!"private".equals(chatType)) {
                return Optional.of("AI requests are available only in a private chat with this bot.");
            }
            synchronized (aiRequestLock) {
                pendingAiQuestions.remove(userId);
            }
            return Optional.of("Pending AI question cleared. Nothing was sent to the provider.");
        }
        if (command.equals("/stop")) {
            synchronized (aiRequestLock) {
                consentedUsers.remove(userId);
                pendingAiQuestions.remove(userId);
            }
            return Optional.of("AI consent cleared and any pending question discarded. Nothing pending was sent to the provider.");
        }
        if (command.equals("/ai-confirm")) {
            if (!"private".equals(chatType)) {
                return Optional.of("AI requests are available only in a private chat with this bot.");
            }
            String question;
            synchronized (aiRequestLock) {
                if (!consentedUsers.contains(userId)) {
                    pendingAiQuestions.remove(userId);
                    return Optional.of("AI consent is not active. Read /ai-help and send /consent before staging a new question.");
                }
                question = pendingAiQuestions.remove(userId);
                if (question == null) {
                    return Optional.of("No pending AI question. Send /ask <question> first; each question needs its own /ai-confirm.");
                }
            }
            return Optional.of(claude.answer(question));
        }
        if (command.equals("/human")) {
            if (!"private".equals(chatType)) {
                return Optional.of("Human follow-up requests are available only in a private chat with this bot.");
            }
            synchronized (aiRequestLock) {
                pendingAiQuestions.remove(userId);
                humanHandoffUsers.add(userId);
            }
            return Optional.of("Human follow-up marked in this demo. No operator/helpdesk queue is connected, so nobody has been notified. Connect an authorized helpdesk handoff before using this with customers. No AI request was sent.");
        }
        if (command.equals("/file-help")) {
            if ("private".equals(chatType)) {
                disclosedFileUsers.add(userId);
            }
            return Optional.of("File intake is private-chat only and accepts small .txt, .log, .java, .json or .csv documents. After /file-consent, a submitted file is read briefly as text to count lines and detect a likely stack trace; it is deleted immediately and is not sent to AI. Use /file-stop to revoke consent.");
        }
        if (command.equals("/file-consent")) {
            if (!"private".equals(chatType)) {
                return Optional.of("File intake consent is available only in a private chat with this bot.");
            }
            if (!disclosedFileUsers.contains(userId)) {
                return Optional.of("Send /file-help first to read how submitted files are processed.");
            }
            fileConsentedUsers.add(userId);
            return Optional.of("File intake consent recorded in volatile demo memory. Eligible files you send directly to this private chat are read as text, summarized as counts only, and deleted immediately. Nothing is sent to AI. Use /file-stop to revoke.");
        }
        if (command.equals("/file-stop")) {
            fileConsentedUsers.remove(userId);
            return Optional.of("File intake consent cleared.");
        }
        if (command.startsWith("/ask ")) {
            if (!"private".equals(chatType)) {
                return Optional.of("For privacy, /ask is disabled in groups. Open a private chat with the bot.");
            }
            String question = command.substring("/ask ".length()).strip();
            if (question.isBlank() || question.length() > 4_000) {
                return Optional.of("Please send a question of up to 4000 characters.");
            }
            synchronized (aiRequestLock) {
                if (!consentedUsers.contains(userId)) {
                    return Optional.of("First read /ai-help, then send /consent if you agree.");
                }
                if (pendingAiQuestions.containsKey(userId)) {
                    return Optional.of("A question is already pending. Send /ai-confirm to send that exact question, or /ai-cancel to discard it.");
                }
                if (pendingAiQuestions.size() >= MAX_PENDING_AI_QUESTIONS) {
                    return Optional.of("The demo cannot stage another AI question right now. Try again later.");
                }
                pendingAiQuestions.put(userId, question);
            }
            return Optional.of("Review the exact question you submitted. If you want to send only that text to the configured Anthropic API for an answer, send /ai-confirm. Send /ai-cancel to discard it. No provider call has happened yet.");
        }
        return Optional.empty();
    }

    public void sendOrLog(String chatId, String text) {
        telegram.sendOrLog(chatId, text);
    }

    public boolean hasFileConsent(String userId) {
        return fileConsentedUsers.contains(userId);
    }

    public boolean hasHumanHandoffRequest(String userId) {
        return humanHandoffUsers.contains(userId);
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
