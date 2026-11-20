package example.telegram;

/** Fakeable seam for the two Bot API operations used by the Stars lifecycle demonstration. */
public interface TelegramStarsGateway {
    void answerPreCheckoutQuery(String queryId, boolean approved, String errorMessage);

    boolean refundStarPayment(String userId, String chargeId);
}
