package example.telegram;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

/** Bounded, process-local shared state for the credential-free Mini App example. */
@Service
public class SharedAvailabilityBoardService {
    private static final int MAX_BOARDS = 500;
    private static final int MAX_CONTRIBUTORS_PER_BOARD = 50;
    private static final int MAX_SLOTS_PER_CONTRIBUTOR = 8;
    private static final int MAX_SLOT_LENGTH = 64;

    private final Map<String, Board> boards = new LinkedHashMap<>();

    public synchronized BoardSnapshot snapshot(String chatInstance, String userId) {
        return snapshotFor(boardFor(chatInstance), userId);
    }

    /**
     * Applies one complete contribution atomically. A stale version is rejected unless the
     * requested contribution already matches current state, in which case it is a safe retry.
     */
    public synchronized UpdateResult update(
            String chatInstance, String userId, long expectedVersion, List<String> requestedSlots) {
        List<String> slots = validateSlots(requestedSlots);
        if (chatInstance == null || chatInstance.isBlank() || userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Chat context and user are required");
        }
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("Expected version must not be negative");
        }

        Board board = boardFor(chatInstance);
        List<String> currentSlots = board.contributions.getOrDefault(userId, List.of());
        if (currentSlots.equals(slots)) {
            return new UpdateResult(UpdateStatus.NO_CHANGE, snapshotFor(board, userId));
        }
        if (expectedVersion != board.version) {
            return new UpdateResult(UpdateStatus.CONFLICT, snapshotFor(board, userId));
        }
        if (!board.contributions.containsKey(userId)
                && !slots.isEmpty()
                && board.contributions.size() >= MAX_CONTRIBUTORS_PER_BOARD) {
            return new UpdateResult(UpdateStatus.CAPACITY_REACHED, snapshotFor(board, userId));
        }

        if (slots.isEmpty()) {
            board.contributions.remove(userId);
        } else {
            board.contributions.put(userId, slots);
        }
        board.version++;
        return new UpdateResult(UpdateStatus.APPLIED, snapshotFor(board, userId));
    }

    private Board boardFor(String chatInstance) {
        Board board = boards.computeIfAbsent(chatInstance, ignored -> new Board());
        while (boards.size() > MAX_BOARDS) {
            boards.remove(boards.keySet().iterator().next());
        }
        return board;
    }

    private BoardSnapshot snapshotFor(Board board, String userId) {
        Map<String, Integer> totals = new TreeMap<>();
        board.contributions.values().forEach(slots -> slots.forEach(
                slot -> totals.merge(slot, 1, Integer::sum)));
        return new BoardSnapshot(board.version, Map.copyOf(totals),
                board.contributions.getOrDefault(userId, List.of()));
    }

    private List<String> validateSlots(List<String> requestedSlots) {
        if (requestedSlots == null || requestedSlots.size() > MAX_SLOTS_PER_CONTRIBUTOR) {
            throw new IllegalArgumentException("At most eight availability slots may be submitted");
        }
        List<String> slots = new ArrayList<>();
        for (String requestedSlot : requestedSlots) {
            if (requestedSlot == null || requestedSlot.isBlank()
                    || requestedSlot.length() > MAX_SLOT_LENGTH) {
                throw new IllegalArgumentException("Availability slots must be non-empty and at most 64 characters");
            }
            String slot = requestedSlot.strip();
            if (slots.contains(slot)) {
                throw new IllegalArgumentException("Availability slots must be unique");
            }
            slots.add(slot);
        }
        return List.copyOf(slots);
    }

    private static final class Board {
        private long version;
        private final Map<String, List<String>> contributions = new LinkedHashMap<>();
    }

    public record BoardSnapshot(long version, Map<String, Integer> slotCounts, List<String> mySlots) {}

    public record UpdateResult(UpdateStatus status, BoardSnapshot snapshot) {}

    public enum UpdateStatus {
        APPLIED,
        NO_CHANGE,
        CONFLICT,
        CAPACITY_REACHED
    }
}
