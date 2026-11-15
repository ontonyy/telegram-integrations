package example.telegram;

import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(
        name = {"telegram.send-enabled", "telegram.outbox.worker-enabled"}, havingValue = "true")
public class TelegramOutboxSchedule {
    private final NotificationOutboxDispatcher dispatcher;

    public TelegramOutboxSchedule(NotificationOutboxDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelayString = "${telegram.outbox.poll-delay:PT1S}")
    public void dispatchOne() {
        dispatcher.dispatchOne(Instant.now());
    }
}
