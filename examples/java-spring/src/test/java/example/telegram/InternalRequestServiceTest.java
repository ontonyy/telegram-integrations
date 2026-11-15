package example.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class InternalRequestServiceTest {
    private static final String APPROVAL_CHAT = "-900";
    private MutableClock clock;
    private RecordingTelegram telegram;
    private InternalRequestService service;
    private long nextUpdateId;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-11-15T12:00:00Z"));
        telegram = new RecordingTelegram();
        service = new InternalRequestService(
                this::rolesFor, telegram, APPROVAL_CHAT, clock);
    }

    @Test
    void configuredIdentityDirectoryMapsServerSideAllowlistToRoles() {
        ConfiguredInternalRoleDirectory directory = new ConfiguredInternalRoleDirectory(
                "10001,10002,not-an-id", "20001,10002");

        assertEquals(Set.of(InternalRoleDirectory.Role.EMPLOYEE), directory.rolesFor("10001"));
        assertEquals(Set.of(InternalRoleDirectory.Role.MANAGER), directory.rolesFor("20001"));
        assertEquals(Set.of(InternalRoleDirectory.Role.EMPLOYEE, InternalRoleDirectory.Role.MANAGER),
                directory.rolesFor("10002"));
        assertTrue(directory.rolesFor("30001").isEmpty());
    }

    @Test
    void requiresPrivateEmployeeInitiationAndRoutesMinimalSyntheticRequest() {
        assertTrue(handle("/internal-help", "group", "-10", "100").orElseThrow()
                .contains("only in a private chat"));
        assertTrue(handle("/request IT|Synthetic laptop issue", "private", "100", "999")
                .orElseThrow().contains("server-side identity configuration"));
        assertTrue(handle("/request IT|Synthetic laptop issue", "private", "100", "100")
                .orElseThrow().contains("Request created"));

        assertEquals(1, telegram.keyboardSends);
        assertTrue(telegram.lastText.contains("Category: IT"));
        assertTrue(telegram.lastText.contains("Synthetic laptop issue"));
        assertFalse(telegram.lastText.contains("employee 100"));
        String reference = referenceFrom(telegram.lastText);
        assertTrue(handle("/request-status " + reference, "private", "100", "100")
                .orElseThrow().contains("PENDING"));
        assertTrue(service.auditEntries().getFirst().toString().contains("submit"));
        assertFalse(service.auditEntries().getFirst().toString().contains("Synthetic laptop issue"));
    }

    @Test
    void requiresConfiguredManagerIdentityAndChatAndASeparateConfirmation() {
        handle("/request ACCESS|Synthetic test access", "private", "100", "100");
        String reference = referenceFrom(telegram.lastText);

        assertTrue(service.handleCallback(APPROVAL_CHAT, "999", "employee_action|" + reference + "|approve")
                .callbackText().contains("authorized manager"));
        assertTrue(service.handleCallback("-elsewhere", "200", "employee_action|" + reference + "|approve")
                .callbackText().contains("not available in this chat"));
        InternalRequestService.CallbackResult selected = service.handleCallback(
                APPROVAL_CHAT, "200", "employee_action|" + reference + "|approve");
        assertFalse(selected.changed());
        assertTrue(selected.callbackText().contains("confirmation prompt"));
        assertTrue(handle("/request-status " + reference, "private", "100", "100")
                .orElseThrow().contains("PENDING"));

        String confirmData = telegram.lastKeyboard.getFirst().getFirst().callbackData();
        assertTrue(service.handleCallback(APPROVAL_CHAT, "201", confirmData).callbackText()
                .contains("another manager"));
        InternalRequestService.CallbackResult confirmed = service.handleCallback(APPROVAL_CHAT, "200", confirmData);
        assertTrue(confirmed.changed());
        assertTrue(confirmed.updatedMessage().contains("APPROVED"));
        assertTrue(service.handleCallback(APPROVAL_CHAT, "200", confirmData).callbackText()
                .contains("no longer pending"));
        assertTrue(handle("/request-status " + reference, "private", "100", "100")
                .orElseThrow().contains("APPROVED"));
        assertEquals(2, service.auditEntries().size());
        assertEquals("approve", service.auditEntries().getLast().action());
        assertEquals("APPROVED", service.auditEntries().getLast().outcome());
    }

    @Test
    void expiresConfirmationAndAllowsOnlyTheRequesterOrAConfiguredManagerToReadStatus() {
        handle("/request EQUIPMENT|Synthetic keyboard request", "private", "100", "100");
        String reference = referenceFrom(telegram.lastText);
        assertTrue(handle("/request-status " + reference, "private", "101", "101")
                .orElseThrow().contains("not authorized"));
        service.handleCallback(APPROVAL_CHAT, "200", "employee_action|" + reference + "|reject");
        String confirmData = telegram.lastKeyboard.getFirst().getFirst().callbackData();
        clock.advance(Duration.ofMinutes(11));

        assertTrue(service.handleCallback(APPROVAL_CHAT, "200", confirmData).callbackText().contains("expired"));
        assertTrue(handle("/request-status " + reference, "private", "100", "100")
                .orElseThrow().contains("PENDING"));
        assertTrue(handle("/request-status " + reference, "private", "200", "200")
                .orElseThrow().contains("PENDING"));
    }

    @Test
    void rejectsInvalidAndOverlongRequestFieldsWithoutStoringRequests() {
        assertTrue(handle("/request VACATION|Synthetic text", "private", "100", "100")
                .orElseThrow().contains("Allowed categories"));
        assertTrue(handle("/request IT|" + "x".repeat(301), "private", "100", "100")
                .orElseThrow().contains("Allowed categories"));
        assertEquals(0, telegram.keyboardSends);
        assertTrue(service.auditEntries().isEmpty());
    }

    @Test
    void confirmedRejectionUpdatesStatusAndAuditOutcome() {
        handle("/request OTHER|Synthetic test request", "private", "100", "100");
        String reference = referenceFrom(telegram.lastText);
        service.handleCallback(APPROVAL_CHAT, "200", "employee_action|" + reference + "|reject");
        String confirmData = telegram.lastKeyboard.getFirst().getFirst().callbackData();

        InternalRequestService.CallbackResult rejected = service.handleCallback(APPROVAL_CHAT, "200", confirmData);

        assertTrue(rejected.updatedMessage().contains("REJECTED"));
        assertTrue(handle("/request-status " + reference, "private", "100", "100")
                .orElseThrow().contains("REJECTED"));
        assertEquals("REJECTED", service.auditEntries().getLast().outcome());
    }

    @Test
    void retryOfTheSameSubmissionUpdateReusesReferenceAfterManagerNotificationFailure() {
        telegram.failNextKeyboardSend = true;
        long updateId = 9001;
        assertThrows(IllegalStateException.class, () -> service.handleMessage(
                "/request IT|Synthetic retry request", "private", "100", "100", updateId));
        String reference = referenceFrom(telegram.lastText);

        assertTrue(service.handleMessage(
                "/request IT|Synthetic retry request", "private", "100", "100", updateId)
                .orElseThrow().contains("Request already received"));

        assertEquals(2, telegram.keyboardSends);
        assertEquals(1, service.auditEntries().size());
        assertTrue(service.handleMessage("/request-status " + reference, "private", "100", "100", 9002)
                .orElseThrow().contains("PENDING"));
    }

    private Set<InternalRoleDirectory.Role> rolesFor(String userId) {
        return switch (userId) {
            case "100", "101" -> Set.of(InternalRoleDirectory.Role.EMPLOYEE);
            case "200", "201" -> Set.of(InternalRoleDirectory.Role.MANAGER);
            default -> Set.of();
        };
    }

    private java.util.Optional<String> handle(String text, String chatType, String chatId, String userId) {
        return service.handleMessage(text, chatType, chatId, userId, ++nextUpdateId);
    }

    private String referenceFrom(String text) {
        return text.substring("Internal request ".length(), text.indexOf('\n'));
    }

    private static final class RecordingTelegram extends TelegramBotClient {
        private int keyboardSends;
        private boolean failNextKeyboardSend;
        private String lastText;
        private java.util.List<java.util.List<InlineButton>> lastKeyboard;

        private RecordingTelegram() {
            super("", false, "https://api.telegram.org", Duration.ofSeconds(1), new ObjectMapper(),
                    request -> new TransportResponse(200, "{}"));
        }

        @Override
        public void sendMessageWithInlineKeyboardOrLog(
                String chatId, String text, java.util.List<java.util.List<InlineButton>> keyboard) {
            keyboardSends++;
            lastText = text;
            lastKeyboard = keyboard;
            if (failNextKeyboardSend) {
                failNextKeyboardSend = false;
                throw new IllegalStateException("synthetic manager notification failure");
            }
        }
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> current;

        private MutableClock(Instant initial) {
            current = new AtomicReference<>(initial);
        }

        private void advance(Duration duration) {
            current.updateAndGet(value -> value.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current.get();
        }
    }
}
