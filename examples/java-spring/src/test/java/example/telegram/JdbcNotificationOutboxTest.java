package example.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.springframework.core.io.ClassPathResource;
import java.time.Duration;
import java.time.Instant;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class JdbcNotificationOutboxTest {
    private JdbcTemplate jdbc;
    private JdbcNotificationOutbox outbox;
    private final Instant now = Instant.parse("2026-01-10T12:00:00Z");

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:outbox-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        outbox = new JdbcNotificationOutbox(jdbc);
    }

    @Test
    void stableEventIdCreatesOnlyOneLogicalDelivery() {
        assertTrue(outbox.enqueue("event-1", "chat-demo", "synthetic message", now, now.plusSeconds(3600)));
        assertFalse(outbox.enqueue("event-1", "chat-demo", "synthetic message", now, now.plusSeconds(3600)));

        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM telegram_notification_outbox WHERE event_id = 'event-1'", Integer.class));
    }

    @Test
    void pendingNotificationSurvivesOpeningANewDatabaseClient(@TempDir Path directory) {
        String url = "jdbc:h2:file:" + directory.resolve("outbox");
        DriverManagerDataSource firstDataSource = dataSource(url);
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(firstDataSource);
        JdbcNotificationOutbox firstClient = new JdbcNotificationOutbox(new JdbcTemplate(firstDataSource));
        firstClient.enqueue("event-persist", "chat-demo", "synthetic persisted message", now, now.plusSeconds(3600));

        JdbcTemplate reopenedClient = new JdbcTemplate(dataSource(url));

        assertEquals("PENDING", reopenedClient.queryForObject(
                "SELECT state FROM telegram_notification_outbox WHERE event_id = ?", String.class, "event-persist"));
    }

    @Test
    void terminalRetentionDeletesOnlyCompletedRows() {
        outbox.enqueue("event-a-terminal", "chat-demo", "synthetic message", now, now.plusSeconds(3600));
        outbox.enqueue("event-z-pending", "chat-demo", "synthetic message", now, now.plusSeconds(3600));
        OutboxMessage claimed = outbox.claimNext(now, Duration.ofSeconds(30)).orElseThrow();
        outbox.markSent("event-a-terminal", claimed.attempts());

        assertEquals(1, outbox.purgeTerminalBefore(now.plusSeconds(1)));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM telegram_notification_outbox WHERE event_id = 'event-a-terminal'", Integer.class));
        assertEquals("PENDING", state("event-z-pending"));
    }

    @Test
    void dueRetryUsesProviderDelayAndCanCompleteLater() {
        outbox.enqueue("event-2", "chat-demo", "synthetic message", now, now.plusSeconds(3600));
        NotificationOutboxDispatcher dispatcher = dispatcher((chatId, text) ->
                NotificationDeliveryResult.retryable(Duration.ofSeconds(17)), 3);

        assertTrue(dispatcher.dispatchOne(now));
        assertEquals("RETRY", state("event-2"));
        assertEquals(now.plusSeconds(17), dueAt("event-2"));
        assertFalse(dispatcher.dispatchOne(now.plusSeconds(16)));

        NotificationOutboxDispatcher succeeding = dispatcher((chatId, text) -> NotificationDeliveryResult.sent(), 3);
        assertTrue(succeeding.dispatchOne(now.plusSeconds(17)));
        assertEquals("SENT", state("event-2"));
        assertEquals(2, attempts("event-2"));
    }

    @Test
    void permanentFailureDoesNotRetry() {
        outbox.enqueue("event-3", "chat-demo", "synthetic message", now, now.plusSeconds(3600));
        NotificationOutboxDispatcher dispatcher = dispatcher((chatId, text) ->
                NotificationDeliveryResult.permanent(), 5);

        assertTrue(dispatcher.dispatchOne(now));
        assertEquals("FAILED", state("event-3"));
        assertFalse(dispatcher.dispatchOne(now.plusSeconds(3600)));
    }

    @Test
    void expiresMessagesBeforeDeliveryAndStopsAfterAttemptLimit() {
        outbox.enqueue("event-expired", "chat-demo", "synthetic message", now, now.plusSeconds(5));
        assertEquals(1, outbox.expireDue(now.plusSeconds(5)));
        assertEquals("EXPIRED", state("event-expired"));

        outbox.enqueue("event-limit", "chat-demo", "synthetic message", now, now.plusSeconds(3600));
        NotificationOutboxDispatcher dispatcher = dispatcher((chatId, text) ->
                NotificationDeliveryResult.retryable(Duration.ZERO), 2);
        assertTrue(dispatcher.dispatchOne(now));
        assertEquals("RETRY", state("event-limit"));
        assertTrue(dispatcher.dispatchOne(now.plusSeconds(2)));
        assertEquals("FAILED", state("event-limit"));
        assertEquals("attempt_limit", failure("event-limit"));
    }

    @Test
    void expiredLeaseCanBeClaimedAgainAfterWorkerCrash() {
        outbox.enqueue("event-lease", "chat-demo", "synthetic message", now, now.plusSeconds(3600));
        OutboxMessage first = outbox.claimNext(now, Duration.ofSeconds(30)).orElseThrow();
        assertEquals(1, first.attempts());
        assertTrue(outbox.claimNext(now.plusSeconds(29), Duration.ofSeconds(30)).isEmpty());

        OutboxMessage reclaimed = outbox.claimNext(now.plusSeconds(30), Duration.ofSeconds(30)).orElseThrow();
        assertEquals(2, reclaimed.attempts());
        outbox.markSent("event-lease", first.attempts());
        assertEquals("SENDING", state("event-lease"));
        outbox.markSent("event-lease", reclaimed.attempts());
        assertEquals("SENT", state("event-lease"));
    }

    @Test
    void dryRunDispatcherDoesNotClaimOrConsumeAnAttempt() {
        outbox.enqueue("event-dry-run", "chat-demo", "synthetic message", now, now.plusSeconds(3600));
        NotificationOutboxDispatcher dispatcher = new NotificationOutboxDispatcher(
                outbox, (chatId, text) -> NotificationDeliveryResult.sent(),
                new NotificationOutboxPolicy(3, Duration.ofSeconds(2), Duration.ofSeconds(30),
                        Duration.ofSeconds(30), Duration.ofHours(24), Duration.ofSeconds(10)),
                false);

        assertFalse(dispatcher.dispatchOne(now));
        assertEquals("PENDING", state("event-dry-run"));
        assertEquals(0, attempts("event-dry-run"));
    }

    @Test
    void concurrentWorkersClaimAnEventAtMostOnceDuringItsLease() throws Exception {
        outbox.enqueue("event-race", "chat-demo", "synthetic message", now, now.plusSeconds(3600));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> claims = List.of(
                    executor.submit(() -> claimAtSameTime(ready, start)),
                    executor.submit(() -> claimAtSameTime(ready, start)));
            ready.await();
            start.countDown();
            long successfulClaims = 0;
            for (Future<Boolean> claim : claims) {
                if (claim.get()) successfulClaims++;
            }
            assertEquals(1, successfulClaims);
        }
    }

    private boolean claimAtSameTime(CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        return outbox.claimNext(now, Duration.ofSeconds(30)).isPresent();
    }

    private NotificationOutboxDispatcher dispatcher(NotificationSender sender, int maxAttempts) {
        return new NotificationOutboxDispatcher(
                outbox, sender,
                new NotificationOutboxPolicy(maxAttempts, Duration.ofSeconds(2), Duration.ofSeconds(30),
                        Duration.ofSeconds(30), Duration.ofHours(24), Duration.ofSeconds(10)),
                true);
    }

    private String state(String eventId) {
        return jdbc.queryForObject(
                "SELECT state FROM telegram_notification_outbox WHERE event_id = ?", String.class, eventId);
    }

    private Instant dueAt(String eventId) {
        return jdbc.queryForObject("SELECT next_attempt_at FROM telegram_notification_outbox WHERE event_id = ?",
                (rs, row) -> rs.getTimestamp(1).toInstant(), eventId);
    }

    private int attempts(String eventId) {
        return jdbc.queryForObject("SELECT attempts FROM telegram_notification_outbox WHERE event_id = ?",
                Integer.class, eventId);
    }

    private String failure(String eventId) {
        return jdbc.queryForObject("SELECT last_failure_kind FROM telegram_notification_outbox WHERE event_id = ?",
                String.class, eventId);
    }

    private DriverManagerDataSource dataSource(String url) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl(url);
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
    }
}
