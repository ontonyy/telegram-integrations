package example.telegram;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Process-local fake Bot API adapter. It records simulated pre-checkout answers and refund results;
 * it never creates invoices, calls Telegram, or moves Stars.
 */
@Component
public class SimulatedTelegramStarsGateway implements TelegramStarsGateway {
    private static final int MAX_RECORDED_OPERATIONS = 1_000;
    private final Map<String, TelegramStarsPaymentService.PreCheckoutAnswer> answers = new LinkedHashMap<>();
    private final Set<RefundKey> refunds = new LinkedHashSet<>();

    @Override
    public synchronized void answerPreCheckoutQuery(String queryId, boolean approved, String errorMessage) {
        answers.put(queryId, new TelegramStarsPaymentService.PreCheckoutAnswer(
                queryId, approved, errorMessage == null ? "" : errorMessage));
        trimAnswers();
    }

    @Override
    public synchronized boolean refundStarPayment(String userId, String chargeId) {
        refunds.add(new RefundKey(userId, chargeId));
        while (refunds.size() > MAX_RECORDED_OPERATIONS) {
            refunds.remove(refunds.iterator().next());
        }
        return true;
    }

    private void trimAnswers() {
        while (answers.size() > MAX_RECORDED_OPERATIONS) {
            answers.remove(answers.keySet().iterator().next());
        }
    }

    private record RefundKey(String userId, String chargeId) {}
}
