package example.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TelegramStarsPaymentServiceTest {
    private static final Instant START = Instant.parse("2026-11-20T12:00:00Z");
    private final MutableClock clock = new MutableClock(START);
    private final RecordingStarsGateway gateway = new RecordingStarsGateway();
    private TelegramStarsPaymentService payments;

    @BeforeEach
    void setUp() {
        payments = new TelegramStarsPaymentService(gateway, clock);
    }

    @Test
    void createsOnlyAPrivateSyntheticInvoiceIntentAndProvidesSupportInstructions() {
        String intent = payments.handleCommand("/buy-demo", "private", "1001").orElseThrow();

        assertTrue(intent.contains("STARS-000001"));
        assertTrue(intent.contains("50 XTR"));
        assertTrue(intent.contains("No Telegram invoice or payment was created"));
        assertEquals(TelegramStarsPaymentService.OrderStatus.PENDING_CHECKOUT,
                payments.findOrderStatus("STARS-000001", "1001").orElseThrow());
        assertEquals(0, gateway.preCheckoutAnswers.size());
        assertEquals(0, gateway.refundCalls);
        assertTrue(payments.handleCommand("/paysupport", "private", "1001")
                .orElseThrow().contains("synthetic demo only"));
        assertTrue(payments.handleCommand("/buy-demo", "group", "1001")
                .orElseThrow().contains("private chat"));
        assertEquals(Optional.empty(), payments.findOrderStatus("STARS-000002", "1001"));
    }

    @Test
    void approvesAValidOwnedStarsCheckoutWithoutFulfillingBeforePayment() {
        createOrder();

        TelegramStarsPaymentService.PreCheckoutResult result = payments.handlePreCheckout(
                new TelegramStarsPaymentService.PreCheckoutQuery(
                        "query-1", "1001", "STARS-000001", "XTR", 50));

        assertTrue(result.approved());
        assertEquals(TelegramStarsPaymentService.OrderStatus.CHECKOUT_APPROVED,
                payments.findOrderStatus("STARS-000001", "1001").orElseThrow());
        assertEquals(List.of(new TelegramStarsPaymentService.PreCheckoutAnswer("query-1", true, "")),
                gateway.preCheckoutAnswers);
        assertEquals(0, payments.fulfillmentCount());
    }

    @Test
    void rejectsUnknownOwnerAmountCurrencyAndExpiredOrders() {
        createOrder();
        TelegramStarsPaymentService.PreCheckoutResult wrongCurrency = payments.handlePreCheckout(
                new TelegramStarsPaymentService.PreCheckoutQuery(
                        "query-currency", "1001", "STARS-000001", "USD", 50));
        assertFalse(wrongCurrency.approved());
        assertEquals(TelegramStarsPaymentService.OrderStatus.CHECKOUT_REJECTED,
                payments.findOrderStatus("STARS-000001", "1001").orElseThrow());

        createOrder();
        TelegramStarsPaymentService.PreCheckoutResult wrongAmount = payments.handlePreCheckout(
                new TelegramStarsPaymentService.PreCheckoutQuery(
                        "query-amount", "1001", "STARS-000002", "XTR", 49));
        assertFalse(wrongAmount.approved());
        assertEquals(TelegramStarsPaymentService.OrderStatus.CHECKOUT_REJECTED,
                payments.findOrderStatus("STARS-000002", "1001").orElseThrow());

        createOrder();
        TelegramStarsPaymentService.PreCheckoutResult wrongOwner = payments.handlePreCheckout(
                new TelegramStarsPaymentService.PreCheckoutQuery(
                        "query-owner", "2002", "STARS-000003", "XTR", 50));
        assertFalse(wrongOwner.approved());
        assertEquals(TelegramStarsPaymentService.OrderStatus.PENDING_CHECKOUT,
                payments.findOrderStatus("STARS-000003", "1001").orElseThrow());

        createOrder();
        clock.advanceSeconds(15 * 60 + 1);
        TelegramStarsPaymentService.PreCheckoutResult expired = payments.handlePreCheckout(
                new TelegramStarsPaymentService.PreCheckoutQuery(
                        "query-expired", "1001", "STARS-000004", "XTR", 50));
        assertFalse(expired.approved());
        assertEquals(TelegramStarsPaymentService.OrderStatus.EXPIRED,
                payments.findOrderStatus("STARS-000004", "1001").orElseThrow());
        assertEquals(4, gateway.preCheckoutAnswers.size());
        assertTrue(gateway.preCheckoutAnswers.stream().noneMatch(TelegramStarsPaymentService.PreCheckoutAnswer::approved));
    }

    @Test
    void checksTheTenSecondResponseBudgetWithAClockInsteadOfSleeping() {
        createOrder();
        Instant requestReceivedAt = clock.instant();
        clock.advanceSeconds(11);

        TelegramStarsPaymentService.PreCheckoutResult result = payments.handlePreCheckout(
                new TelegramStarsPaymentService.PreCheckoutQuery(
                        "query-late", "1001", "STARS-000001", "XTR", 50),
                requestReceivedAt);

        assertFalse(result.approved());
        assertEquals(TelegramStarsPaymentService.PreCheckoutStatus.DEADLINE_MISSED, result.status());
        assertEquals(TelegramStarsPaymentService.OrderStatus.PENDING_CHECKOUT,
                payments.findOrderStatus("STARS-000001", "1001").orElseThrow());
        assertTrue(gateway.preCheckoutAnswers.isEmpty());
    }

    @Test
    void doesNotMutateOrderWhenTheDeadlineIsMissedOrGatewayAnswerFails() {
        createOrder();
        Instant receivedAt = clock.instant();
        clock.advanceSeconds(11);
        payments.handlePreCheckout(new TelegramStarsPaymentService.PreCheckoutQuery(
                "query-late-invalid", "1001", "STARS-000001", "USD", 50), receivedAt);
        assertEquals(TelegramStarsPaymentService.OrderStatus.PENDING_CHECKOUT,
                payments.findOrderStatus("STARS-000001", "1001").orElseThrow());

        gateway.throwOnPreCheckout = true;
        assertThrows(IllegalStateException.class, () -> payments.handlePreCheckout(
                new TelegramStarsPaymentService.PreCheckoutQuery(
                        "query-retry", "1001", "STARS-000001", "USD", 50)));
        assertEquals(TelegramStarsPaymentService.OrderStatus.PENDING_CHECKOUT,
                payments.findOrderStatus("STARS-000001", "1001").orElseThrow());
    }

    @Test
    void fulfillsOnlyAfterAValidSuccessfulPaymentAndDeduplicatesChargeIds() {
        createOrder();
        TelegramStarsPaymentService.SuccessfulPayment payment = successfulPayment(
                "1001", "STARS-000001", "synthetic-charge-1", "XTR", 50);

        assertEquals(TelegramStarsPaymentService.PaymentStatus.CHECKOUT_NOT_APPROVED,
                payments.handleSuccessfulPayment(payment).status());
        assertEquals(0, payments.fulfillmentCount());

        approveOrder("STARS-000001", "query-1");
        assertEquals(TelegramStarsPaymentService.PaymentStatus.FULFILLED,
                payments.handleSuccessfulPayment(payment).status());
        assertEquals(1, payments.fulfillmentCount());
        assertEquals(TelegramStarsPaymentService.OrderStatus.FULFILLED,
                payments.findOrderStatus("STARS-000001", "1001").orElseThrow());

        assertEquals(TelegramStarsPaymentService.PaymentStatus.DUPLICATE,
                payments.handleSuccessfulPayment(payment).status());
        assertEquals(TelegramStarsPaymentService.PaymentStatus.INVALID,
                payments.handleSuccessfulPayment(successfulPayment(
                        "1001", "STARS-000001", "synthetic-charge-2", "XTR", 50)).status());
        assertEquals(TelegramStarsPaymentService.PaymentStatus.INVALID,
                payments.handleSuccessfulPayment(successfulPayment(
                        "1001", "STARS-000001", "synthetic-charge-3", "XTR", 49)).status());
        assertEquals(1, payments.fulfillmentCount());
    }

    @Test
    void authorizesRefundsByOwnerAndDoesNotRepeatAConfirmedRefund() {
        createOrder();
        approveOrder("STARS-000001", "query-1");
        payments.handleSuccessfulPayment(successfulPayment(
                "1001", "STARS-000001", "synthetic-charge-1", "XTR", 50));

        assertTrue(payments.handleCommand("/refund-demo STARS-000001", "private", "2002")
                .orElseThrow().contains("not the buyer"));
        assertEquals(0, gateway.refundCalls);

        assertTrue(payments.handleCommand("/refund-demo STARS-000001", "private", "1001")
                .orElseThrow().contains("simulated refund was confirmed"));
        assertEquals(TelegramStarsPaymentService.OrderStatus.REFUNDED,
                payments.findOrderStatus("STARS-000001", "1001").orElseThrow());

        assertTrue(payments.handleCommand("/refund-demo STARS-000001", "private", "1001")
                .orElseThrow().contains("already confirmed"));
        assertEquals(1, gateway.refundCalls);
    }

    @Test
    void failedRefundCanBeRetriedButAnAmbiguousRefundIsNotRetriedAutomatically() {
        createOrder();
        approveOrder("STARS-000001", "query-1");
        payments.handleSuccessfulPayment(successfulPayment(
                "1001", "STARS-000001", "synthetic-charge-1", "XTR", 50));
        gateway.refundResult = false;

        payments.handleCommand("/refund-demo STARS-000001", "private", "1001");
        assertEquals(TelegramStarsPaymentService.OrderStatus.REFUND_FAILED,
                payments.findOrderStatus("STARS-000001", "1001").orElseThrow());
        gateway.refundResult = true;
        payments.handleCommand("/refund-demo STARS-000001", "private", "1001");
        assertEquals(TelegramStarsPaymentService.OrderStatus.REFUNDED,
                payments.findOrderStatus("STARS-000001", "1001").orElseThrow());
        assertEquals(2, gateway.refundCalls);

        createOrder();
        approveOrder("STARS-000002", "query-2");
        payments.handleSuccessfulPayment(successfulPayment(
                "1001", "STARS-000002", "synthetic-charge-2", "XTR", 50));
        gateway.throwOnRefund = true;
        payments.handleCommand("/refund-demo STARS-000002", "private", "1001");
        assertEquals(TelegramStarsPaymentService.OrderStatus.REFUND_UNKNOWN,
                payments.findOrderStatus("STARS-000002", "1001").orElseThrow());
        payments.handleCommand("/refund-demo STARS-000002", "private", "1001");
        assertEquals(3, gateway.refundCalls);
    }

    private String createOrder() {
        return payments.handleCommand("/buy-demo", "private", "1001").orElseThrow();
    }

    private void approveOrder(String orderReference, String queryId) {
        assertTrue(payments.handlePreCheckout(new TelegramStarsPaymentService.PreCheckoutQuery(
                queryId, "1001", orderReference, "XTR", 50)).approved());
    }

    private TelegramStarsPaymentService.SuccessfulPayment successfulPayment(
            String userId, String payload, String chargeId, String currency, int totalAmount) {
        return new TelegramStarsPaymentService.SuccessfulPayment(
                userId, payload, currency, totalAmount, chargeId);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private static final class RecordingStarsGateway implements TelegramStarsGateway {
        private final List<TelegramStarsPaymentService.PreCheckoutAnswer> preCheckoutAnswers = new ArrayList<>();
        private int refundCalls;
        private boolean refundResult = true;
        private boolean throwOnRefund;
        private boolean throwOnPreCheckout;

        @Override
        public void answerPreCheckoutQuery(String queryId, boolean approved, String errorMessage) {
            if (throwOnPreCheckout) {
                throw new IllegalStateException("synthetic pre-checkout answer failure");
            }
            preCheckoutAnswers.add(new TelegramStarsPaymentService.PreCheckoutAnswer(
                    queryId, approved, errorMessage));
        }

        @Override
        public boolean refundStarPayment(String userId, String chargeId) {
            refundCalls++;
            if (throwOnRefund) {
                throw new IllegalStateException("synthetic ambiguous refund result");
            }
            return refundResult;
        }
    }
}
