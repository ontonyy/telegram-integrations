package example.telegram;

public interface BusinessMessageSender {
    void sendBusinessMessageOrLog(String connectionId, String chatId, String text);
}
