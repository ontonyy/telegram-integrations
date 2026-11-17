package example.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

class MiniAppControllerTest {
    private static final String BOT_TOKEN = "synthetic-test-token";
    private final ObjectMapper mapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MiniAppController controller = new MiniAppController(
                BOT_TOKEN, new TelegramInitDataValidator(), new MiniAppRequestService(), mapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void createsRequestAndScopesStatusToVerifiedTelegramUser() throws Exception {
        String initData = initData(101, Instant.now().getEpochSecond());
        String created = mockMvc.perform(post("/api/miniapp/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "initData", initData,
                                "title", "Synthetic appointment",
                                "details", "Wednesday afternoon"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andReturn().getResponse().getContentAsString();
        String requestId = mapper.readTree(created).path("requestId").asText();

        mockMvc.perform(post("/api/miniapp/requests/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "initData", initData, "requestId", requestId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Synthetic appointment"));

        mockMvc.perform(post("/api/miniapp/requests/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "initData", initData(202, Instant.now().getEpochSecond()),
                                "requestId", requestId))))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsTamperedAndStaleLaunchDataForWrites() throws Exception {
        String fresh = initData(101, Instant.now().getEpochSecond());
        mockMvc.perform(post("/api/miniapp/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "initData", fresh.replace("query_id=sample", "query_id=changed"),
                                "title", "Synthetic request", "details", ""))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/miniapp/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "initData", initData(101, Instant.now().getEpochSecond() - 3_600),
                                "title", "Synthetic request", "details", ""))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validatesLaunchSessionFromSignedUserRatherThanClientSuppliedUserId() throws Exception {
        String response = mockMvc.perform(post("/api/miniapp/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "initData", initData(101, Instant.now().getEpochSecond()),
                                "telegramUserId", "999"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals("101", mapper.readTree(response).path("telegramUserId").asText());
    }

    private String initData(long userId, long authDate) {
        Map<String, String> values = Map.of(
                "auth_date", Long.toString(authDate),
                "query_id", "sample",
                "user", "{\"id\":" + userId + ",\"first_name\":\"Test\"}");
        String checkString = new TreeMap<>(values).entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + "\n" + right)
                .orElseThrow();
        byte[] secret = hmac("WebAppData".getBytes(StandardCharsets.UTF_8), BOT_TOKEN);
        String hash = HexFormatSupport.toHex(hmac(secret, checkString));
        String encoded = new TreeMap<>(values).entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElseThrow();
        return encoded + "&hash=" + hash;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private byte[] hmac(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
