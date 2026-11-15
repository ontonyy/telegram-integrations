package example.telegram;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** HubSpot CRM contact upsert using the current stable date-versioned API. */
@Component
public class HubSpotContactsApi implements HubSpotContactsGateway {
    private final String accessToken;
    private final String ownerId;
    private final String apiBaseUrl;
    private final String apiVersion;
    private final Duration requestTimeout;
    private final ObjectMapper mapper;
    private final HttpTransport transport;

    public HubSpotContactsApi(
            @Value("${hubspot.access-token:}") String accessToken,
            @Value("${hubspot.owner-id:}") String ownerId,
            @Value("${hubspot.api-base-url:https://api.hubapi.com}") String apiBaseUrl,
            @Value("${hubspot.api-version:2026-09}") String apiVersion,
            @Value("${hubspot.request-timeout:PT10S}") Duration requestTimeout,
            ObjectMapper mapper,
            HttpTransport transport) {
        this.accessToken = accessToken;
        this.ownerId = ownerId.strip();
        this.apiBaseUrl = requireHttpsOrigin(apiBaseUrl);
        if (apiVersion == null || !apiVersion.matches("\\d{4}-(03|09)")) {
            throw new IllegalArgumentException("HubSpot API version must use YYYY-03 or YYYY-09 format");
        }
        this.apiVersion = apiVersion;
        this.requestTimeout = requestTimeout;
        this.mapper = mapper;
        this.transport = transport;
    }

    @Override
    public String upsert(SalesLeadService.Contact contact) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("HubSpot access token is not configured");
        }
        if (ownerId.isBlank()) {
            throw new IllegalStateException("HubSpot owner assignment is not configured");
        }
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("email", contact.email());
        properties.put("firstname", contact.firstName());
        properties.put("lastname", contact.lastName());
        if (!contact.company().isBlank()) {
            properties.put("company", contact.company());
        }
        if (!ownerId.isBlank()) {
            properties.put("hubspot_owner_id", ownerId);
        }

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("id", contact.email());
        input.put("idProperty", "email");
        input.put("properties", properties);
        Map<String, Object> payload = Map.of("inputs", java.util.List.of(input));
        try {
            String body = mapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl + "/crm/objects/" + apiVersion + "/contacts/batch/upsert"))
                    .timeout(requestTimeout)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            TransportResponse response = transport.send(request);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("HubSpot contact upsert returned HTTP " + response.statusCode());
            }
            JsonNode result = mapper.readTree(response.body()).path("results").path(0);
            if (result.path("errors").isArray() && !result.path("errors").isEmpty()) {
                throw new IllegalStateException("HubSpot rejected the contact upsert");
            }
            String contactId = result.path("id").asText("");
            if (!contactId.matches("[0-9]{1,32}")) {
                throw new IllegalStateException("HubSpot returned no valid contact reference");
            }
            return contactId;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("HubSpot contact upsert was interrupted");
        } catch (Exception exception) {
            if (exception instanceof IllegalStateException illegalStateException) {
                throw illegalStateException;
            }
            throw new IllegalStateException("HubSpot contact upsert failed");
        }
    }

    private String requireHttpsOrigin(String value) {
        URI uri = URI.create(value == null ? "" : value.strip());
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || (uri.getRawPath() != null && !uri.getRawPath().isEmpty() && !"/".equals(uri.getRawPath()))) {
            throw new IllegalArgumentException("HubSpot API base URL must be an HTTPS origin");
        }
        return (uri.getScheme() + "://" + uri.getRawAuthority()).replaceAll("/+$", "");
    }
}
