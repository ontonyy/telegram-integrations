package example.telegram;

@FunctionalInterface
public interface GroupVoteAuthorizer {
    boolean mayVote(String chatId, String userId);
}
