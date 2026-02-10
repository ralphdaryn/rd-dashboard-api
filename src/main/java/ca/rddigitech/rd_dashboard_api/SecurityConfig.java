package ca.rddigitech.rd_dashboard_api;

import java.util.Set;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

@Configuration
public class SecurityConfig {

  private static final Set<String> ALLOWED_EMAILS = Set.of(
    "stepxstepclub@gmail.com",
    "ralphdarync@gmail.com"
  );

  // ✅ Must match the namespace used in your Auth0 Action
  private static final String EMAIL_CLAIM = "https://rddigitech.ca/email";

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
      .csrf(csrf -> csrf.disable())
      .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/health", "/api/health/**").permitAll()
        .requestMatchers("/api/dashboard/**").access(onlyAllowedEmails())
        .anyRequest().denyAll()
      )
      .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));

    return http.build();
  }

  @Bean
  AuthorizationManager<RequestAuthorizationContext> onlyAllowedEmails() {
    return (authenticationSupplier, context) -> {
      Authentication auth = authenticationSupplier.get();
      if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
        return new AuthorizationDecision(false);
      }

      Jwt jwt = jwtAuth.getToken();

      // ✅ Read the namespaced email claim injected by Auth0 Action
      String email = jwt.getClaimAsString(EMAIL_CLAIM);
      if (email == null) return new AuthorizationDecision(false);

      return new AuthorizationDecision(ALLOWED_EMAILS.contains(email.toLowerCase()));
    };
  }
}