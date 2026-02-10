package ca.rddigitech.rd_dashboard_api;

import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

  private static final Set<String> ALLOWED_EMAILS = Set.of(
    "stepxstepclub@gmail.com",
    "ralphdarync@gmail.com"
  );

  // ✅ If your Auth0 Action injects this, we’ll use it
  private static final String EMAIL_CLAIM = "https://rddigitech.ca/email";

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
      // ✅ CORS enabled (needed for browser calls)
      .cors(Customizer.withDefaults())

      .csrf(csrf -> csrf.disable())
      .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .authorizeHttpRequests(auth -> auth
        // ✅ Allow preflight
        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

        // ✅ Public health
        .requestMatchers("/api/health", "/api/health/**").permitAll()

        // ✅ TEMP DIAG: only require a valid JWT (NO email allowlist yet)
        // If this works (200), then we flip back to .access(onlyAllowedEmails())
        .requestMatchers("/api/dashboard/**").authenticated()

        .anyRequest().denyAll()
      )
      // ✅ JWT validation + explicit scope mapping
      .oauth2ResourceServer(oauth2 -> oauth2
        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
      )
      // ✅ Make errors clearer (instead of confusing "insufficient_scope")
      .exceptionHandling(eh -> eh
        .authenticationEntryPoint((req, res, ex) -> res.sendError(401, "Unauthorized"))
        .accessDeniedHandler((req, res, ex) -> res.sendError(403, "Forbidden"))
      );

    return http.build();
  }

  /**
   * ✅ Force Spring to read authorities from the "scope" claim.
   * Auth0 access tokens commonly include: "scope": "openid profile email"
   */
  @Bean
  Converter<Jwt, JwtAuthenticationToken> jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter gac = new JwtGrantedAuthoritiesConverter();
    gac.setAuthoritiesClaimName("scope");   // Auth0 uses "scope"
    gac.setAuthorityPrefix("SCOPE_");       // Spring convention

    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(gac);

    return jwt -> (JwtAuthenticationToken) converter.convert(jwt);
  }

  // ✅ CORS config used by http.cors()
  @Bean
  CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();

    config.setAllowedOrigins(List.of(
      "https://stepbystepclub.ca",
      "https://www.stepbystepclub.ca",
      "http://localhost:5173",
      "http://localhost:8888",
      "http://localhost:5174"
    ));

    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    config.setExposedHeaders(List.of("Authorization"));

    // ✅ You’re using Bearer tokens (not cookies) so keep false
    config.setAllowCredentials(false);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }

  /**
   * ✅ Your original allowlist gate (we’ll re-enable after the diag passes).
   * To re-enable, change:
   *   .requestMatchers("/api/dashboard/**").authenticated()
   * back to:
   *   .requestMatchers("/api/dashboard/**").access(onlyAllowedEmails())
   */
  @Bean
  AuthorizationManager<RequestAuthorizationContext> onlyAllowedEmails() {
    return (authenticationSupplier, context) -> {
      Authentication auth = authenticationSupplier.get();
      if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
        return new AuthorizationDecision(false);
      }

      Jwt jwt = jwtAuth.getToken();

      // ✅ 1) Try your namespaced claim (if Action added it)
      String email = jwt.getClaimAsString(EMAIL_CLAIM);

      // ✅ 2) Fallback to standard Auth0 claim
      if (email == null) {
        email = jwt.getClaimAsString("email");
      }

      if (email == null) return new AuthorizationDecision(false);

      return new AuthorizationDecision(ALLOWED_EMAILS.contains(email.toLowerCase()));
    };
  }
}