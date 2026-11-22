package example.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.util.UriComponentsBuilder;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

@SpringBootTest(properties = {
        "telegram.login.oidc.enabled=true",
        "telegram.login.oidc.client-id=synthetic-client",
        "telegram.login.oidc.client-secret=synthetic-secret",
        "telegram.login.oidc.scopes=openid",
        "spring.datasource.url=jdbc:h2:mem:telegram-login-oidc;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class TelegramLoginOidcSecurityTest {
    private static final KeyPair SIGNING_KEY = newKeyPair();
    private static final RSAKey JWK = new RSAKey.Builder((RSAPublicKey) SIGNING_KEY.getPublic())
            .keyID("synthetic-signing-key").build();
    private static HttpServer issuer;
    private static String issuerUri;
    private static final AtomicInteger tokenRequests = new AtomicInteger();
    private static final AtomicReference<String> activeNonce = new AtomicReference<>();
    private static final AtomicReference<String> activeChallenge = new AtomicReference<>();
    private static final AtomicReference<String> activeRedirectUri = new AtomicReference<>();
    @Autowired
    private MockMvc mockMvc;

    static {
        try {
            issuer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            issuer.createContext("/.well-known/openid-configuration", exchange -> respond(exchange, 200,
                    "{\"issuer\":\"" + issuerUriPlaceholder() + "\","
                            + "\"authorization_endpoint\":\"" + issuerUriPlaceholder() + "/auth\","
                            + "\"token_endpoint\":\"" + issuerUriPlaceholder() + "/token\","
                            + "\"jwks_uri\":\"" + issuerUriPlaceholder() + "/jwks\","
                            + "\"response_types_supported\":[\"code\"],"
                            + "\"subject_types_supported\":[\"public\"],"
                            + "\"id_token_signing_alg_values_supported\":[\"RS256\"],"
                            + "\"token_endpoint_auth_methods_supported\":[\"client_secret_basic\"],"
                            + "\"code_challenge_methods_supported\":[\"S256\"]}"));
            issuer.createContext("/jwks", exchange -> respond(exchange, 200, new JWKSet(JWK).toString()));
            issuer.createContext("/auth", exchange -> respond(exchange, 404, "{}"));
            issuer.createContext("/token", TelegramLoginOidcSecurityTest::tokenResponse);
            issuer.start();
            issuerUri = "http://127.0.0.1:" + issuer.getAddress().getPort();
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @AfterAll
    static void stopIssuer() {
        issuer.stop(0);
    }

    @DynamicPropertySource
    static void issuerProperties(DynamicPropertyRegistry registry) {
        registry.add("telegram.login.oidc.issuer-uri", () -> issuerUri);
    }

    @Test
    void startsAuthorizationCodeFlowWithPkceAndMinimumScope() throws Exception {
        AuthorizationRequest authorization = beginLogin();

        assertTrue(authorization.parameters().containsKey("state"));
        assertTrue(authorization.parameters().containsKey("nonce"));
        assertTrue(authorization.parameters().containsKey("code_challenge"));
        assertEquals("S256", authorization.parameters().get("code_challenge_method"));
        assertEquals("openid", authorization.parameters().get("scope"));
        assertEquals("synthetic-client", authorization.parameters().get("client_id"));
        assertFalse(authorization.parameters().containsKey("client_secret"));
        assertFalse(authorization.parameters().get("scope").contains("profile"));
        assertFalse(authorization.parameters().get("scope").contains("phone"));
    }

    @Test
    void acceptsMockIssuerIdTokenAndExposesSubjectWithoutGrantingRoles() throws Exception {
        AuthorizationRequest authorization = beginLogin();
        int requestsBefore = tokenRequests.get();
        MvcResult login = callback(authorization, "valid")
                .andExpect(status().is3xxRedirection()).andReturn();
        assertEquals(requestsBefore + 1, tokenRequests.get(), "the authorization code must be exchanged once");
        assertEquals("/", login.getResponse().getRedirectedUrl(), "OIDC callback must authenticate successfully");

        mockMvc.perform(get("/login-demo/me")
                        .session((MockHttpSession) login.getRequest().getSession(false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("synthetic-telegram-subject"))
                .andExpect(jsonPath("$.roles").doesNotExist());
    }

    @Test
    void rejectsStateMismatchBeforeExchangingAuthorizationCode() throws Exception {
        AuthorizationRequest authorization = beginLogin();
        int requestsBefore = tokenRequests.get();

        mockMvc.perform(get("/login/oauth2/code/telegram")
                        .param("code", "valid")
                        .param("state", "wrong-state")
                        .session(authorization.session()))
                .andExpect(status().is3xxRedirection());

        assertEquals(requestsBefore, tokenRequests.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"nonce", "signature", "issuer", "audience", "expired", "future-iat"})
    void rejectsInvalidIdToken(String scenario) throws Exception {
        AuthorizationRequest authorization = beginLogin();

        callback(authorization, scenario).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/login-demo/me").session(authorization.session()))
                .andExpect(status().is3xxRedirection());
    }

    private AuthorizationRequest beginLogin() throws Exception {
        MvcResult result = mockMvc.perform(get("/oauth2/authorization/telegram"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        URI redirect = URI.create(result.getResponse().getRedirectedUrl());
        Map<String, String> parameters = UriComponentsBuilder.fromUri(redirect).build()
                .getQueryParams().toSingleValueMap().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> URLDecoder.decode(entry.getValue(), StandardCharsets.UTF_8)));
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session, "authorization redirect must retain state in a server-side session");
        return new AuthorizationRequest(parameters, session);
    }

    private org.springframework.test.web.servlet.ResultActions callback(
            AuthorizationRequest authorization, String scenario) throws Exception {
        activeNonce.set(authorization.parameters().get("nonce"));
        activeChallenge.set(authorization.parameters().get("code_challenge"));
        activeRedirectUri.set(authorization.parameters().get("redirect_uri"));
        return mockMvc.perform(get("/login/oauth2/code/telegram")
                .param("code", scenario)
                .param("state", authorization.parameters().get("state"))
                .session(authorization.session()));
    }

    private static void tokenResponse(HttpExchange exchange) throws IOException {
        tokenRequests.incrementAndGet();
        String expectedAuthorization = "Basic " + Base64.getEncoder().encodeToString(
                "synthetic-client:synthetic-secret".getBytes(StandardCharsets.UTF_8));
        if (!expectedAuthorization.equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
            respond(exchange, 401, "invalid_client");
            return;
        }
        String form = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> values = UriComponentsBuilder.newInstance().query(form)
                .build().getQueryParams().toSingleValueMap();
        String verifier = values.get("code_verifier");
        if (verifier == null || !activeChallenge.get().equals(pkceChallenge(verifier))) {
            respond(exchange, 400, "invalid_pkce_verifier");
            return;
        }
        if (!activeRedirectUri.get().equals(URLDecoder.decode(values.get("redirect_uri"), StandardCharsets.UTF_8))) {
            respond(exchange, 400, "redirect_uri_mismatch");
            return;
        }
        String code = URLDecoder.decode(values.get("code"), StandardCharsets.UTF_8);
        String idToken = signedIdToken(code);
        respond(exchange, 200, "{\"access_token\":\"synthetic-access-token\","
                + "\"token_type\":\"Bearer\",\"expires_in\":3600,\"scope\":\"openid\",\"id_token\":\""
                + idToken + "\"}");
    }

    private static String signedIdToken(String scenario) {
        try {
            Instant now = Instant.now();
            String issuerClaim = "issuer".equals(scenario) ? "https://wrong.example" : issuerUri;
            String audience = "audience".equals(scenario) ? "another-client" : "synthetic-client";
            Instant issueTime = "future-iat".equals(scenario) ? now.plusSeconds(120) : now;
            Instant expiration = "expired".equals(scenario)
                    ? now.minusSeconds(30)
                    : now.plusSeconds("future-iat".equals(scenario) ? 600 : 300);
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(issuerClaim)
                    .subject("synthetic-telegram-subject")
                    .audience(audience)
                    .issueTime(java.util.Date.from(issueTime))
                    .expirationTime(java.util.Date.from(expiration))
                    .claim("nonce", "nonce".equals(scenario) ? "wrong-nonce" : activeNonce.get())
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID("signature".equals(scenario) ? "unknown-key" : JWK.getKeyID()).build(), claims);
            jwt.sign(new RSASSASigner(SIGNING_KEY.getPrivate()));
            return jwt.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String issuerUriPlaceholder() {
        return "http://127.0.0.1:" + issuer.getAddress().getPort();
    }

    private static String pkceChallenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static KeyPair newKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private record AuthorizationRequest(Map<String, String> parameters, MockHttpSession session) {}
}
