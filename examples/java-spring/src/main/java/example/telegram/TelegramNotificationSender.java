package example.telegram;

import org.springframework.stereotype.Component;

@Component
public class TelegramNotificationSender implements NotificationSender {
    private final TelegramBotClient telegram;

    public TelegramNotificationSender(TelegramBotClient telegram) {
        this.telegram = telegram;
    }

    @Override
    public NotificationDeliveryResult send(String chatId, String text) {
        return telegram.sendForOutbox(chatId, text);
    }
}
