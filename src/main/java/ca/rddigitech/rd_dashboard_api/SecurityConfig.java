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

  // 🔐 ONLY these Auth0 users can access the dashboard
  // ✅ Replace these with the REAL `user.sub` values
  private static final Set<String> ALLOWED_SUBS = Set.of(
    "REPLACE_WITH_RALPH_SUB",
    "REPLACE_WITH_CLIENT_SUB"
  );

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
      .csrf(csrf -> csrf.disable())
      .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/health", "/api/health/**").permitAll()

        // 🔒 JWT required AND sub must be allow-listed
        .requestMatchers("/api/dashboard/**").access(onlyAllowedSubs())

        .anyRequest().denyAll()
      )
      .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));

    return http.build();
  }

  @Bean
  AuthorizationManager<RequestAuthorizationContext> onlyAllowedSubs() {
    return (authenticationSupplier, context) -> {

      Authentication auth = authenticationSupplier.get();
      if (auth == null || !auth.isAuthenticated()) {
        return new AuthorizationDecision(false);
      }

      if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
        return new AuthorizationDecision(false);
      }

      Jwt jwt = jwtAuth.getToken();

      // "sub" is guaranteed by Auth0
      String sub = jwt.getSubject();

      return new AuthorizationDecision(ALLOWED_SUBS.contains(sub));
    };
  }
}