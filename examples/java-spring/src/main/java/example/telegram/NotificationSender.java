package example.telegram;

@FunctionalInterface
public interface NotificationSender {
    NotificationDeliveryResult send(String chatId, String text);
}
