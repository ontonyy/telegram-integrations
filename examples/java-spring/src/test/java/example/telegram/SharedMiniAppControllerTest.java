package example.telegram;

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

class SharedMiniAppControllerTest {
    private static final String BOT_TOKEN = "synthetic-test-token";
    private static final String ALLOWED_CHAT = "synthetic-chat-instance-1";
    private final ObjectMapper mapper = new ObjectMapper();
    private SharedAvailabilityBoardService boards;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        boards = new SharedAvailabilityBoardService();
        SharedMiniAppAuthorizer authorizer = (chat, user) -> ALLOWED_CHAT.equals(chat)
                && ("101".equals(user) || "202".equals(user));
        SharedMiniAppController controller = new SharedMiniAppController(
                BOT_TOKEN, new TelegramInitDataValidator(), authorizer, boards, mapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void exposesAggregateStateAndOwnContributionToAnAuthorizedGroupUser() throws Exception {
        mockMvc.perform(post("/api/miniapp/shared/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "initData", initData(101, ALLOWED_CHAT, "group"),
                                "expectedVersion", 0,
                                "slots", new String[] {"Mon 10:00", "Tue 11:00"}))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.mySlots[0]").value("Mon 10:00"));

        mockMvc.perform(post("/api/miniapp/shared/board")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "initData", initData(202, ALLOWED_CHAT, "group")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.slotCounts['Mon 10:00']").value(1))
                .andExpect(jsonPath("$.mySlots").isEmpty());
    }

    @Test
    void rejectsInvalidPrivateLaunchAndUnlistedChatOrUser() throws Exception {
        postBoard(initData(101, ALLOWED_CHAT, "private"))
                .andExpect(status().isUnauthorized());
        postBoard(initData(101, "synthetic-chat-instance-2", "group"))
                .andExpect(status().isForbidden());
        postBoard(initData(303, ALLOWED_CHAT, "group"))
                .andExpect(status().isForbidden());
        postBoard("auth_date=1&hash=invalid")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reportsStaleVersionAndAllowsAnIdenticalRequestRetry() throws Exception {
        String firstRequest = mapper.writeValueAsString(Map.of(
                "initData", initData(101, ALLOWED_CHAT, "supergroup"),
                "expectedVersion", 0,
                "slots", new String[] {"Mon 10:00"}));
        mockMvc.perform(post("/api/miniapp/shared/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(post("/api/miniapp/shared/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "initData", initData(202, ALLOWED_CHAT, "supergroup"),
                                "expectedVersion", 0,
                                "slots", new String[] {"Tue 11:00"}))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.board.version").value(1))
                .andExpect(jsonPath("$.board.slotCounts['Mon 10:00']").value(1));

        mockMvc.perform(post("/api/miniapp/shared/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void rejectsInvalidContributionsWithoutChangingTheBoard() throws Exception {
        mockMvc.perform(post("/api/miniapp/shared/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "initData", initData(101, ALLOWED_CHAT, "group"),
                                "expectedVersion", 0,
                                "slots", new String[] {"Mon", "Mon"}))))
                .andExpect(status().isBadRequest());

        postBoard(initData(101, ALLOWED_CHAT, "group"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.slotCounts").isEmpty());
    }

    private org.springframework.test.web.servlet.ResultActions postBoard(String initData) throws Exception {
        return mockMvc.perform(post("/api/miniapp/shared/board")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("initData", initData))));
    }

    private String initData(long userId, String chatInstance, String chatType) {
        Map<String, String> values = Map.of(
                "auth_date", Long.toString(Instant.now().getEpochSecond()),
                "chat_instance", chatInstance,
                "chat_type", chatType,
                "query_id", "synthetic-query",
                "user", "{\"id\":" + userId + ",\"first_name\":\"Synthetic\"}");
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

    private byte[] hmac(byte[] key, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
