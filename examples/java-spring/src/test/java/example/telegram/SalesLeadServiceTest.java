package example.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class SalesLeadServiceTest {
    private RecordingTelegram telegram;
    private FakeHubSpot hubspot;
    private SalesLeadService service;

    @BeforeEach
    void setUp() {
        telegram = new RecordingTelegram();
        hubspot = new FakeHubSpot();
        service = new SalesLeadService(hubspot, telegram, "-900", "Demo sales queue");
    }

    @Test
    void requiresDisclosureAndSeparateConsentBeforeSendingOnlyUserProvidedFields() {
        assertTrue(service.handle("/sales-submit a@example.test|Ada|Lovelace|Analytical", "private", "42", "42")
                .orElseThrow().contains("Read /sales"));
        assertEquals(0, hubspot.upserts);

        String disclosure = service.handle("/sales", "private", "42", "42").orElseThrow();
        assertTrue(disclosure.contains("does not subscribe you to marketing"));
        assertTrue(disclosure.contains("No Telegram username, profile, group membership, or chat history"));
        service.handle("/sales-consent", "private", "42", "42");
        service.handle("/sales-submit a@example.test|Ada|Lovelace|Analytical", "private", "42", "42");

        assertEquals(new SalesLeadService.Contact("a@example.test", "Ada", "Lovelace", "Analytical"),
                hubspot.lastContact);
        assertEquals(1, hubspot.upserts);
        assertEquals(1, telegram.sendCalls);
        assertTrue(telegram.lastText.contains("Demo sales queue"));
        assertTrue(telegram.lastText.contains("HubSpot contact reference: 1001"));
        assertFalse(telegram.lastText.contains("a@example.test"));
        assertFalse(telegram.lastText.contains("Ada"));
        assertFalse(telegram.lastText.contains("42"));
    }

    @Test
    void consentIsPrivateAndRevocableAndSubmissionIsValidated() {
        service.handle("/sales", "private", "42", "42");
        assertTrue(service.handle("/sales-consent", "group", "-77", "42").orElseThrow()
                .contains("only in a private chat"));
        service.handle("/sales-consent", "private", "42", "42");
        assertTrue(service.handle("/sales-submit not-an-email|Ada|Lovelace|-", "private", "42", "42")
                .orElseThrow().contains("Use /sales-submit"));
        service.handle("/sales-stop", "private", "42", "42");

        assertTrue(service.handle("/sales-submit a@example.test|Ada|Lovelace|-", "private", "42", "42")
                .orElseThrow().contains("Read /sales"));
        assertEquals(0, hubspot.upserts);
        assertEquals(0, telegram.sendCalls);
    }

    @Test
    void crmFailureDoesNotNotifyManagerAndRepeatContactDoesNotDuplicateNoticeInProcess() {
        service.handle("/sales", "private", "42", "42");
        service.handle("/sales-consent", "private", "42", "42");
        hubspot.fail = true;
        assertTrue(service.handle("/sales-submit a@example.test|Ada|Lovelace|-", "private", "42", "42")
                .orElseThrow().contains("CRM could not accept"));
        assertEquals(0, telegram.sendCalls);

        hubspot.fail = false;
        service.handle("/sales-submit a@example.test|Ada|Lovelace|-", "private", "42", "42");
        service.handle("/sales-submit a@example.test|Ada|Lovelace|-", "private", "42", "42");
        assertEquals(3, hubspot.upserts);
        assertEquals(1, telegram.sendCalls);
    }

    @Test
    void onlyAcceptsPrivateChatsAndRequiresManagerRoutingConfiguration() {
        assertTrue(service.handle("/sales", "supergroup", "-77", "42").orElseThrow()
                .contains("only in a private chat"));
        SalesLeadService unconfigured = new SalesLeadService(hubspot, telegram, "", "Demo sales queue");
        unconfigured.handle("/sales", "private", "43", "43");
        unconfigured.handle("/sales-consent", "private", "43", "43");

        assertTrue(unconfigured.handle("/sales-submit a@example.test|Ada|Lovelace|-", "private", "43", "43")
                .orElseThrow().contains("routing is not configured"));
    }

    private static final class FakeHubSpot implements HubSpotContactsGateway {
        private final Map<String, String> idsByEmail = new HashMap<>();
        private int upserts;
        private boolean fail;
        private SalesLeadService.Contact lastContact;

        @Override
        public String upsert(SalesLeadService.Contact contact) {
            upserts++;
            lastContact = contact;
            if (fail) {
                throw new IllegalStateException("synthetic CRM failure");
            }
            return idsByEmail.computeIfAbsent(contact.email(), ignored -> "1001");
        }
    }

    private static final class RecordingTelegram extends TelegramBotClient {
        private int sendCalls;
        private String lastText;

        private RecordingTelegram() {
            super("", false, "https://api.telegram.org", Duration.ofSeconds(1), new ObjectMapper(),
                    request -> new TransportResponse(200, "{}"));
        }

        @Override
        public void sendOrLog(String chatId, String text) {
            sendCalls++;
            lastText = text;
        }
    }
}
