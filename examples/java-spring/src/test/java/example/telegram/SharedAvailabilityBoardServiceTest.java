package example.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class SharedAvailabilityBoardServiceTest {
    private final SharedAvailabilityBoardService boards = new SharedAvailabilityBoardService();

    @Test
    void rejectsStaleWritesAndAllowsAnIdenticalRetryWithoutIncrementingVersion() {
        assertEquals(0, boards.snapshot("chat-a", "101").version());

        SharedAvailabilityBoardService.UpdateResult first = boards.update("chat-a", "101", 0, List.of("Mon 10:00"));
        assertEquals(SharedAvailabilityBoardService.UpdateStatus.APPLIED, first.status());
        assertEquals(1, first.snapshot().version());

        SharedAvailabilityBoardService.UpdateResult duplicate =
                boards.update("chat-a", "101", 0, List.of("Mon 10:00"));
        assertEquals(SharedAvailabilityBoardService.UpdateStatus.NO_CHANGE, duplicate.status());
        assertEquals(1, duplicate.snapshot().version());

        SharedAvailabilityBoardService.UpdateResult stale =
                boards.update("chat-a", "202", 0, List.of("Tue 11:00"));
        assertEquals(SharedAvailabilityBoardService.UpdateStatus.CONFLICT, stale.status());
        assertEquals(1, stale.snapshot().version());
        assertEquals(1, stale.snapshot().slotCounts().get("Mon 10:00"));
        assertEquals(List.of(), stale.snapshot().mySlots());
    }

    @Test
    void updatesAggregateCountsAndCanWithdrawAContribution() {
        boards.update("chat-a", "101", 0, List.of("Mon 10:00", "Tue 11:00"));
        boards.update("chat-a", "202", 1, List.of("Mon 10:00"));

        SharedAvailabilityBoardService.BoardSnapshot shared = boards.snapshot("chat-a", "303");
        assertEquals(2, shared.version());
        assertEquals(2, shared.slotCounts().get("Mon 10:00"));
        assertEquals(1, shared.slotCounts().get("Tue 11:00"));
        assertEquals(List.of(), shared.mySlots());

        SharedAvailabilityBoardService.UpdateResult withdrawn = boards.update("chat-a", "101", 2, List.of());
        assertEquals(SharedAvailabilityBoardService.UpdateStatus.APPLIED, withdrawn.status());
        assertEquals(3, withdrawn.snapshot().version());
        assertEquals(1, withdrawn.snapshot().slotCounts().get("Mon 10:00"));
        assertEquals(null, withdrawn.snapshot().slotCounts().get("Tue 11:00"));
    }

    @Test
    void isolatesBoardsBySignedChatContext() {
        boards.update("chat-a", "101", 0, List.of("Mon 10:00"));

        SharedAvailabilityBoardService.BoardSnapshot anotherChat = boards.snapshot("chat-b", "101");
        assertEquals(0, anotherChat.version());
        assertEquals(0, anotherChat.slotCounts().size());
    }

    @Test
    void validatesEntireContributionBeforeChangingBoardState() {
        assertThrows(IllegalArgumentException.class,
                () -> boards.update("chat-a", "101", 0, List.of("Mon", "Mon")));
        assertThrows(IllegalArgumentException.class,
                () -> boards.update("chat-a", "101", 0, List.of(" ")));
        assertThrows(IllegalArgumentException.class,
                () -> boards.update("chat-a", "101", -1, List.of("Mon")));

        SharedAvailabilityBoardService.BoardSnapshot unchanged = boards.snapshot("chat-a", "101");
        assertEquals(0, unchanged.version());
        assertEquals(0, unchanged.slotCounts().size());
    }

    @Test
    void concurrentWritesAgainstTheSameVersionHaveExactlyOneWinner() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            var first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return boards.update("chat-a", "101", 0, List.of("Mon 10:00"));
            });
            var second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return boards.update("chat-a", "202", 0, List.of("Tue 11:00"));
            });
            ready.await();
            start.countDown();

            var firstResult = first.get();
            var secondResult = second.get();
            int applied = (firstResult.status() == SharedAvailabilityBoardService.UpdateStatus.APPLIED ? 1 : 0)
                    + (secondResult.status() == SharedAvailabilityBoardService.UpdateStatus.APPLIED ? 1 : 0);
            int conflicts = (firstResult.status() == SharedAvailabilityBoardService.UpdateStatus.CONFLICT ? 1 : 0)
                    + (secondResult.status() == SharedAvailabilityBoardService.UpdateStatus.CONFLICT ? 1 : 0);

            assertEquals(1, applied);
            assertEquals(1, conflicts);
            assertEquals(1, boards.snapshot("chat-a", "303").version());
            assertEquals(1, boards.snapshot("chat-a", "303").slotCounts().size());
        }
    }
}
