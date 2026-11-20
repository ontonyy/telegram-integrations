package example.telegram;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Synthetic, in-memory payment lifecycle. No payment or fulfillment call leaves this process. */
@Service
public class TelegramStarsPaymentService {
    private static final int DEMO_PRICE_STARS = 50;
    private static final int MAX_ORDERS = 1_000;
    private static final Duration ORDER_TTL = Duration.ofMinutes(15);
    private static final Duration PRE_CHECKOUT_RESPONSE_BUDGET = Duration.ofSeconds(10);
    private final TelegramStarsGateway gateway;
    private final Clock clock;
    private final Map<String, Order> orders = new LinkedHashMap<>();
    private final Map<String, String> chargeOwners = new LinkedHashMap<>();
    private final Set<String> fulfilledOrders = new LinkedHashSet<>();
    private long orderSequence;

    @Autowired
    public TelegramStarsPaymentService(TelegramStarsGateway gateway) {
        this(gateway, Clock.systemUTC());
    }

    TelegramStarsPaymentService(TelegramStarsGateway gateway, Clock clock) {
        this.gateway = gateway;
        this.clock = clock;
    }

    public synchronized Optional<String> handleCommand(String rawText, String chatType, String userId) {
        if (rawText == null || rawText.isBlank()) {
            return Optional.empty();
        }
        String[] parts = rawText.strip().split("\\s+", 2);
        String command = parts[0].split("@", 2)[0];
        if (!Set.of("/buy-demo", "/refund-demo", "/paysupport").contains(command)) {
            return Optional.empty();
        }
        if (!"private".equals(chatType)) {
            return Optional.of("The Stars lifecycle simulation is available only in a private chat.");
        }
        if (userId == null || userId.isBlank()) {
            return Optional.of("A Telegram user ID is required for this synthetic payment example.");
        }
        return switch (command) {
            case "/buy-demo" -> Optional.of(createDemoOrder(userId));
            case "/refund-demo" -> Optional.of(requestRefund(
                    parts.length == 2 ? parts[1].strip() : "", userId));
            case "/paysupport" -> Optional.of("Payment support: this is a synthetic demo only; no real Stars are accepted. "
                    + "Use /buy-demo to create a local order intent, then /refund-demo <order-reference> after the "
                    + "synthetic payment lifecycle has marked it fulfilled.");
            default -> Optional.empty();
        };
    }

    public synchronized PreCheckoutResult handlePreCheckout(PreCheckoutQuery query) {
        return handlePreCheckout(query, clock.instant());
    }

    /** Accepts a local receive time so the ten-second response budget can be tested without sleeping. */
    public synchronized PreCheckoutResult handlePreCheckout(PreCheckoutQuery query, Instant receivedAt) {
        if (query == null || query.queryId() == null || query.queryId().isBlank()) {
            return new PreCheckoutResult(PreCheckoutStatus.INVALID, false, "A query ID is required.");
        }

        String denial = null;
        OrderStatus statusAfterAnswer = null;
        Order order = query.invoicePayload() == null ? null : orders.get(query.invoicePayload());
        Instant now = clock.instant();
        if (order == null) {
            denial = "This synthetic order does not exist.";
        } else if (!order.userId.equals(query.userId())) {
            denial = "This synthetic order belongs to a different buyer.";
        } else if (now.isAfter(order.expiresAt)) {
            statusAfterAnswer = OrderStatus.EXPIRED;
            denial = "This synthetic order has expired.";
        } else if (order.status == OrderStatus.CHECKOUT_APPROVED
                && query.queryId().equals(order.preCheckoutQueryId)) {
            // A retry for the same query gets the same answer; no second state transition occurs.
        } else if (order.status != OrderStatus.PENDING_CHECKOUT) {
            denial = "This synthetic order is not available for checkout.";
        } else if (!"XTR".equals(query.currency())) {
            statusAfterAnswer = OrderStatus.CHECKOUT_REJECTED;
            denial = "This synthetic digital item requires XTR.";
        } else if (query.totalAmount() != DEMO_PRICE_STARS) {
            statusAfterAnswer = OrderStatus.CHECKOUT_REJECTED;
            denial = "The synthetic order amount does not match.";
        }

        if (receivedAt == null || clock.instant().isAfter(receivedAt.plus(PRE_CHECKOUT_RESPONSE_BUDGET))) {
            return new PreCheckoutResult(PreCheckoutStatus.DEADLINE_MISSED, false,
                    "The local ten-second pre-checkout response budget was exceeded.");
        }

        boolean approved = denial == null;
        String responseText = approved ? "" : denial;
        gateway.answerPreCheckoutQuery(query.queryId(), approved, responseText);
        if (approved && order != null && order.status == OrderStatus.PENDING_CHECKOUT) {
            order.status = OrderStatus.CHECKOUT_APPROVED;
            order.preCheckoutQueryId = query.queryId();
        } else if (!approved && order != null && statusAfterAnswer != null) {
            order.status = statusAfterAnswer;
        }
        return new PreCheckoutResult(
                approved ? PreCheckoutStatus.APPROVED : PreCheckoutStatus.DECLINED,
                approved,
                responseText);
    }

    public synchronized PaymentResult handleSuccessfulPayment(SuccessfulPayment payment) {
        if (payment == null || payment.invoicePayload() == null) {
            return new PaymentResult(PaymentStatus.INVALID, "A synthetic order reference is required.");
        }
        Order order = orders.get(payment.invoicePayload());
        if (order == null) {
            return new PaymentResult(PaymentStatus.UNKNOWN_ORDER, "The synthetic order does not exist.");
        }
        if (!order.userId.equals(payment.userId())) {
            return new PaymentResult(PaymentStatus.INVALID, "The successful-payment user does not match the order.");
        }
        if (!"XTR".equals(payment.currency()) || payment.totalAmount() != DEMO_PRICE_STARS
                || payment.chargeId() == null || payment.chargeId().isBlank()) {
            return new PaymentResult(PaymentStatus.INVALID, "The synthetic payment details do not match the order.");
        }

        String chargeOrder = chargeOwners.get(payment.chargeId());
        if (chargeOrder != null && !chargeOrder.equals(order.reference)) {
            return new PaymentResult(PaymentStatus.INVALID, "This synthetic charge is already linked to another order.");
        }
        if (order.status == OrderStatus.FULFILLED) {
            return order.chargeId.equals(payment.chargeId())
                    ? new PaymentResult(PaymentStatus.DUPLICATE, "This synthetic payment was already fulfilled.")
                    : new PaymentResult(PaymentStatus.INVALID, "A different charge is already linked to this order.");
        }
        if (order.status == OrderStatus.PAID && !order.chargeId.equals(payment.chargeId())) {
            return new PaymentResult(PaymentStatus.INVALID, "A different charge is already linked to this order.");
        }
        if (order.status != OrderStatus.CHECKOUT_APPROVED && order.status != OrderStatus.PAID) {
            return new PaymentResult(PaymentStatus.CHECKOUT_NOT_APPROVED,
                    "The order has no approved synthetic pre-checkout.");
        }

        order.status = OrderStatus.PAID;
        order.chargeId = payment.chargeId();
        chargeOwners.put(payment.chargeId(), order.reference);
        fulfilledOrders.add(order.reference);
        order.status = OrderStatus.FULFILLED;
        return new PaymentResult(PaymentStatus.FULFILLED, "Synthetic digital entitlement granted.");
    }

    public synchronized Optional<OrderStatus> findOrderStatus(String orderReference, String userId) {
        Order order = orders.get(orderReference);
        if (order == null || !order.userId.equals(userId)) {
            return Optional.empty();
        }
        return Optional.of(order.status);
    }

    public synchronized int fulfillmentCount() {
        return fulfilledOrders.size();
    }

    private String createDemoOrder(String userId) {
        if (orders.size() >= MAX_ORDERS) {
            return "The synthetic payment demo is full; try again after its in-memory records are cleared.";
        }
        String reference = "STARS-%06d".formatted(++orderSequence);
        Order order = new Order(reference, userId, clock.instant().plus(ORDER_TTL));
        orders.put(reference, order);
        return "Synthetic invoice intent " + reference + " for " + DEMO_PRICE_STARS
                + " XTR created. No Telegram invoice or payment was created. "
                + "Use this order reference only with the local synthetic update flow.";
    }

    private String requestRefund(String orderReference, String userId) {
        Order order = orders.get(orderReference);
        if (order == null) {
            return "That synthetic order was not found.";
        }
        if (!order.userId.equals(userId)) {
            return "You are not the buyer for that synthetic order.";
        }
        if (order.status == OrderStatus.REFUNDED) {
            return "A simulated refund was already confirmed for this order.";
        }
        if (order.status == OrderStatus.REFUND_UNKNOWN) {
            return "The simulated refund result is unknown; do not retry automatically. Reconcile it manually.";
        }
        if (order.status != OrderStatus.FULFILLED && order.status != OrderStatus.REFUND_FAILED) {
            return "The order must be synthetically fulfilled before it can be refunded.";
        }

        order.status = OrderStatus.REFUND_PENDING;
        try {
            if (gateway.refundStarPayment(userId, order.chargeId)) {
                order.status = OrderStatus.REFUNDED;
                return "The simulated refund was confirmed. No Telegram Stars were moved.";
            }
            order.status = OrderStatus.REFUND_FAILED;
            return "The simulated refund was not confirmed. You may retry this demo request.";
        } catch (RuntimeException exception) {
            order.status = OrderStatus.REFUND_UNKNOWN;
            return "The simulated refund result is unknown; do not retry automatically. Reconcile it manually.";
        }
    }

    private static final class Order {
        private final String reference;
        private final String userId;
        private final Instant expiresAt;
        private OrderStatus status = OrderStatus.PENDING_CHECKOUT;
        private String preCheckoutQueryId;
        private String chargeId;

        private Order(String reference, String userId, Instant expiresAt) {
            this.reference = reference;
            this.userId = userId;
            this.expiresAt = expiresAt;
        }
    }

    public record PreCheckoutQuery(String queryId, String userId, String invoicePayload, String currency, long totalAmount) {}

    public record SuccessfulPayment(String userId, String invoicePayload, String currency, long totalAmount, String chargeId) {}

    public record PreCheckoutAnswer(String queryId, boolean approved, String errorMessage) {}

    public record PreCheckoutResult(PreCheckoutStatus status, boolean approved, String errorMessage) {}

    public record PaymentResult(PaymentStatus status, String message) {}

    public enum OrderStatus {
        PENDING_CHECKOUT,
        CHECKOUT_APPROVED,
        CHECKOUT_REJECTED,
        EXPIRED,
        PAID,
        FULFILLED,
        REFUND_PENDING,
        REFUNDED,
        REFUND_FAILED,
        REFUND_UNKNOWN
    }

    public enum PreCheckoutStatus {
        APPROVED,
        DECLINED,
        DEADLINE_MISSED,
        INVALID
    }

    public enum PaymentStatus {
        FULFILLED,
        DUPLICATE,
        INVALID,
        UNKNOWN_ORDER,
        CHECKOUT_NOT_APPROVED
    }
}
