package example.telegram;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GroupVoteService {
    private static final String QUESTION = "Choose a synthetic maintenance window";
    private static final List<String> OPTIONS = List.of("Tuesday 10:00", "Wednesday 14:00", "Thursday 09:00");
    private static final int MAX_STORED_VOTES = 1_000;

    private final GroupVoteAuthorizer authorizer;
    private final Map<String, Vote> votes = new LinkedHashMap<>();

    public GroupVoteService(GroupVoteAuthorizer authorizer) {
        this.authorizer = authorizer;
    }

    public synchronized VotePrompt start(String chatId, String creatorUserId) {
        String voteId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        votes.put(voteId, new Vote(chatId, creatorUserId));
        trimOldestVoteIfNeeded();
        return new VotePrompt(voteId, promptText(voteId), keyboard(voteId));
    }

    public synchronized VoteResult cast(String chatId, String userId, String callbackData) {
        CallbackData callback = parse(callbackData);
        if (callback == null) {
            return VoteResult.denied("This vote action is invalid.");
        }
        Vote vote = votes.get(callback.voteId());
        if (vote == null) {
            return VoteResult.denied("This vote is no longer available.");
        }
        if (!vote.chatId.equals(chatId)) {
            return VoteResult.denied("This vote belongs to another chat.");
        }
        if (!authorizer.mayVote(chatId, userId)) {
            return VoteResult.denied("You are not allowed to vote here.");
        }
        if (vote.closed) {
            return VoteResult.denied("This vote is closed.");
        }
        if (callback.optionIndex() < 0 || callback.optionIndex() >= OPTIONS.size()) {
            return VoteResult.denied("This vote option is invalid.");
        }

        Integer previousChoice = vote.choicesByUser.put(userId, callback.optionIndex());
        boolean changed = previousChoice == null || previousChoice != callback.optionIndex();
        String acknowledgement = previousChoice == null
                ? "Vote recorded."
                : changed ? "Your vote was changed." : "Your vote is already recorded.";
        return new VoteResult(acknowledgement, summary(callback.voteId(), vote, false),
                keyboard(callback.voteId()), true);
    }

    public synchronized CloseResult close(String chatId, String userId, String voteId) {
        Vote vote = votes.get(voteId);
        if (vote == null || !vote.chatId.equals(chatId) || !vote.creatorUserId.equals(userId)) {
            return new CloseResult(false, "");
        }
        if (!vote.closed) {
            vote.closed = true;
        }
        return new CloseResult(true, summary(voteId, vote, true));
    }

    private CallbackData parse(String callbackData) {
        if (callbackData == null) {
            return null;
        }
        String[] parts = callbackData.split("\\|", -1);
        if (parts.length != 3 || !"vote".equals(parts[0])) {
            return null;
        }
        try {
            return new CallbackData(parts[1], Integer.parseInt(parts[2]));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String promptText(String voteId) {
        return QUESTION + "\nVote ID: " + voteId + "\nCreator can finish with /closevote " + voteId;
    }

    private List<List<TelegramBotClient.InlineButton>> keyboard(String voteId) {
        List<TelegramBotClient.InlineButton> buttons = new ArrayList<>();
        for (int index = 0; index < OPTIONS.size(); index++) {
            buttons.add(new TelegramBotClient.InlineButton(
                    OPTIONS.get(index), "vote|" + voteId + "|" + index));
        }
        return List.of(buttons);
    }

    private String summary(String voteId, Vote vote, boolean finalResult) {
        int[] counts = new int[OPTIONS.size()];
        vote.choicesByUser.values().forEach(choice -> counts[choice]++);
        StringBuilder text = new StringBuilder(finalResult ? "FINAL RESULTS — " : "Live vote — ")
                .append(QUESTION).append("\nVote ID: ").append(voteId).append("\n");
        for (int index = 0; index < OPTIONS.size(); index++) {
            text.append(OPTIONS.get(index)).append(": ").append(counts[index]).append("\n");
        }
        return text.toString().stripTrailing();
    }

    private void trimOldestVoteIfNeeded() {
        if (votes.size() > MAX_STORED_VOTES) {
            String oldest = votes.keySet().iterator().next();
            votes.remove(oldest);
        }
    }

    public record VotePrompt(String voteId, String text, List<List<TelegramBotClient.InlineButton>> keyboard) {}

    public record VoteResult(
            String callbackText,
            String updatedMessage,
            List<List<TelegramBotClient.InlineButton>> keyboard,
            boolean accepted) {
        private static VoteResult denied(String text) {
            return new VoteResult(text, null, List.of(), false);
        }
    }

    public record CloseResult(boolean authorized, String finalMessage) {}

    private record CallbackData(String voteId, int optionIndex) {}

    private static final class Vote {
        private final String chatId;
        private final String creatorUserId;
        private final Map<String, Integer> choicesByUser = new LinkedHashMap<>();
        private boolean closed;

        private Vote(String chatId, String creatorUserId) {
            this.chatId = chatId;
            this.creatorUserId = creatorUserId;
        }
    }
}
