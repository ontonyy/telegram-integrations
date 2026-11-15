package example.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GroupVoteServiceTest {
    @Test
    void validatesChatAuthorizationAndCallbackAction() {
        GroupVoteService service = new GroupVoteService((chatId, userId) -> "allowed".equals(userId));
        GroupVoteService.VotePrompt prompt = service.start("group-a", "creator");
        String firstOption = prompt.keyboard().getFirst().getFirst().callbackData();

        assertFalse(service.cast("group-b", "allowed", firstOption).accepted());
        assertFalse(service.cast("group-a", "denied", firstOption).accepted());
        assertFalse(service.cast("group-a", "allowed", "unexpected-action").accepted());
        assertTrue(service.cast("group-a", "allowed", firstOption).accepted());
    }

    @Test
    void sameChoiceIsNotCountedTwiceAndClosedVoteRejectsFurtherChanges() {
        GroupVoteService service = new GroupVoteService((chatId, userId) -> true);
        GroupVoteService.VotePrompt prompt = service.start("group", "creator");
        String first = prompt.keyboard().getFirst().get(0).callbackData();
        String second = prompt.keyboard().getFirst().get(1).callbackData();

        GroupVoteService.VoteResult recorded = service.cast("group", "member", first);
        GroupVoteService.VoteResult repeated = service.cast("group", "member", first);
        assertTrue(recorded.updatedMessage().contains("Tuesday 10:00: 1"));
        assertTrue(repeated.callbackText().contains("already recorded"));
        assertTrue(repeated.updatedMessage().contains("Tuesday 10:00: 1"));

        GroupVoteService.CloseResult denied = service.close("group", "member", prompt.voteId());
        assertFalse(denied.authorized());
        GroupVoteService.CloseResult closed = service.close("group", "creator", prompt.voteId());
        assertTrue(closed.authorized());
        assertTrue(closed.finalMessage().startsWith("FINAL RESULTS"));
        assertFalse(service.cast("group", "member", second).accepted());
        assertFalse(service.close("other-group", "creator", prompt.voteId()).authorized());
    }
}
