package example.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class HubSpotContactsApiTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void sendsDateVersionedEmailKeyedUpsertWithExplicitOwnerAndMinimalProperties() throws Exception {
        AtomicReference<HttpRequest> captured = new AtomicReference<>();
        AtomicReference<String> capturedBody = new AtomicReference<>();
        HttpTransport transport = request -> {
            captured.set(request);
            capturedBody.set(body(request));
            return new TransportResponse(200, "{\"results\":[{\"id\":\"1001\"}]}");
        };
        HubSpotContactsApi api = api("synthetic-hubspot-token", "owner-7", transport);

        assertEquals("1001", api.upsert(new SalesLeadService.Contact(
                "lead@example.test", "Ada", "Lovelace", "Analytical")));

        HttpRequest request = captured.get();
        assertEquals("https://api.hubapi.com/crm/objects/2026-09/contacts/batch/upsert",
                request.uri().toString());
        assertEquals("Bearer synthetic-hubspot-token", request.headers().firstValue("Authorization").orElseThrow());
        var tree = mapper.readTree(capturedBody.get());
        assertEquals("email", tree.path("inputs").path(0).path("idProperty").asText());
        assertEquals("lead@example.test", tree.path("inputs").path(0).path("id").asText());
        var properties = tree.path("inputs").path(0).path("properties");
        assertEquals("owner-7", properties.path("hubspot_owner_id").asText());
        assertEquals("Ada", properties.path("firstname").asText());
        assertFalse(properties.has("telegram_id"));
        assertFalse(properties.has("lifecyclestage"));
    }

    @Test
    void reportsProviderFailuresWithoutExposingResponseBodyOrToken() {
        HubSpotContactsApi api = api("synthetic-secret-token", "owner-7",
                request -> new TransportResponse(403, "private CRM diagnostics synthetic-secret-token"));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> api.upsert(new SalesLeadService.Contact("lead@example.test", "Ada", "Lovelace", "")));
        assertTrue(exception.getMessage().contains("HTTP 403"));
        assertFalse(exception.getMessage().contains("synthetic-secret-token"));
        assertFalse(exception.getMessage().contains("private CRM diagnostics"));
    }

    @Test
    void requiresTokenAndConfiguredHubSpotOwnerBeforeNetworkCall() {
        AtomicReference<Boolean> called = new AtomicReference<>(false);
        HubSpotContactsGateway missingToken = api("", "owner-7", request -> {
            called.set(true);
            return new TransportResponse(200, "{}");
        });
        assertThrows(IllegalStateException.class,
                () -> missingToken.upsert(new SalesLeadService.Contact("lead@example.test", "Ada", "Lovelace", "")));
        HubSpotContactsGateway missingOwner = api("synthetic-token", "", request -> {
            called.set(true);
            return new TransportResponse(200, "{}");
        });
        assertThrows(IllegalStateException.class,
                () -> missingOwner.upsert(new SalesLeadService.Contact("lead@example.test", "Ada", "Lovelace", "")));
        assertFalse(called.get());
    }

    private HubSpotContactsApi api(String token, String ownerId, HttpTransport transport) {
        return new HubSpotContactsApi(token, ownerId, "https://api.hubapi.com", "2026-09",
                Duration.ofSeconds(2), mapper, transport);
    }

    private String body(HttpRequest request) throws InterruptedException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CountDownLatch complete = new CountDownLatch(1);
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                byte[] chunk = new byte[item.remaining()];
                item.get(chunk);
                bytes.writeBytes(chunk);
            }

            @Override
            public void onError(Throwable throwable) {
                complete.countDown();
            }

            @Override
            public void onComplete() {
                complete.countDown();
            }
        });
        if (!complete.await(1, java.util.concurrent.TimeUnit.SECONDS)) {
            throw new IllegalStateException("Synthetic request body was not published");
        }
        return bytes.toString(java.nio.charset.StandardCharsets.UTF_8);
    }
}
