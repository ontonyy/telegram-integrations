package example.telegram;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.web.SecurityFilterChain;

/** Optional website login example. Disabled by default and isolated from Mini App initData validation. */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class TelegramLoginOidcConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "telegram.login.oidc", name = "enabled", havingValue = "true")
    ClientRegistrationRepository telegramLoginClientRegistrationRepository(
            @Value("${telegram.login.oidc.issuer-uri}") String issuerUri,
            @Value("${telegram.login.oidc.client-id}") String clientId,
            @Value("${telegram.login.oidc.client-secret}") String clientSecret,
            @Value("${telegram.login.oidc.redirect-uri:}") String configuredRedirectUri,
            @Value("${telegram.login.oidc.scopes:openid}") String configuredScopes) {
        List<String> scopes = Arrays.stream(configuredScopes.split(","))
                .map(String::strip)
                .filter(scope -> !scope.isEmpty())
                .distinct()
                .toList();
        if (!scopes.contains("openid")) {
            throw new IllegalArgumentException("Telegram Login OIDC must request the openid scope");
        }
        ClientRegistration registration = ClientRegistrations.fromIssuerLocation(issuerUri)
                .registrationId("telegram")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(configuredRedirectUri.isBlank()
                        ? "{baseUrl}/login/oauth2/code/{registrationId}"
                        : configuredRedirectUri)
                .scope(scopes)
                .clientSettings(ClientRegistration.ClientSettings.builder().requireProofKey(true).build())
                .build();
        return new InMemoryClientRegistrationRepository(registration);
    }

    @Bean
    @ConditionalOnProperty(prefix = "telegram.login.oidc", name = "enabled", havingValue = "true")
    SecurityFilterChain telegramLoginSecurityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/login-demo/me").authenticated()
                        .anyRequest().permitAll())
                .csrf(csrf -> csrf.ignoringRequestMatchers(
                        "/telegram/webhook", "/jira/webhook", "/grafana/webhook", "/api/miniapp/**"))
                .oauth2Login(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "telegram.login.oidc", name = "enabled", havingValue = "false", matchIfMissing = true)
    SecurityFilterChain demoWithoutLoginSecurityFilterChain(HttpSecurity http) throws Exception {
        // Preserve the existing credential-free webhook/API demonstration behavior when OIDC is off.
        http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
