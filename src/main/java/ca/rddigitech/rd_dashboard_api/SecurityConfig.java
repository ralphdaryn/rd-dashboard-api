package ca.rddigitech.rd_dashboard_api;

import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;

import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

  // ✅ Emails allowed to view dashboards
  private static final Set<String> ALLOWED_EMAILS = Set.of(
    "stepxstepclub@gmail.com",
    "ralphdarync@gmail.com"
  );

  // ✅ Custom Auth0 claim (confirmed in token)
  private static final String EMAIL_CLAIM = "https://rddigitech.ca/email";

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    var ppm = PathPatternRequestMatcher.withDefaults();

    http
      // 🌍 CORS
      .cors(Customizer.withDefaults())

      // 🔒 Stateless API
      .csrf(csrf -> csrf.disable())
      .sessionManagement(sm ->
        sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
      )

      // 🔐 Authorization rules
      .authorizeHttpRequests(auth -> auth
        // Preflight
        .requestMatchers(ppm.matcher(HttpMethod.OPTIONS, "/**")).permitAll()

        // Public health check
        .requestMatchers(ppm.matcher("/api/health")).permitAll()
        .requestMatchers(ppm.matcher("/api/health/**")).permitAll()

        // 🔐 Dashboard = email allowlist
        .requestMatchers(ppm.matcher("/api/dashboard/**"))
          .access(onlyAllowedEmails())

        // Everything else blocked
        .anyRequest().denyAll()
      )

      // 🔑 JWT validation (Auth0)
      .oauth2ResourceServer(oauth2 ->
        oauth2.jwt(Customizer.withDefaults())
      )

      // ❗ Clean error responses
      .exceptionHandling(eh -> eh
        .authenticationEntryPoint(
          (req, res, ex) -> res.sendError(401, "Unauthorized")
        )
        .accessDeniedHandler(
          (req, res, ex) -> res.sendError(403, "Forbidden")
        )
      );

    return http.build();
  }

  // 🌍 CORS configuration
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

    config.setAllowedMethods(
      List.of("GET", "POST", "PUT", "DELETE", "OPTIONS")
    );

    config.setAllowedHeaders(
      List.of("Authorization", "Content-Type")
    );

    config.setExposedHeaders(List.of("Authorization"));
    config.setAllowCredentials(false);

    UrlBasedCorsConfigurationSource source =
      new UrlBasedCorsConfigurationSource();

    source.registerCorsConfiguration("/**", config);
    return source;
  }

  // 🔐 Email allowlist authorization
  @Bean
  AuthorizationManager<RequestAuthorizationContext> onlyAllowedEmails() {
    return (authenticationSupplier, context) -> {
      Authentication auth = authenticationSupplier.get();

      if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
        return new AuthorizationDecision(false);
      }

      Jwt jwt = jwtAuth.getToken();

      // 1️⃣ Custom Auth0 claim
      String email = jwt.getClaimAsString(EMAIL_CLAIM);

      // 2️⃣ Fallback (just in case)
      if (email == null) {
        email = jwt.getClaimAsString("email");
      }

      if (email == null) {
        return new AuthorizationDecision(false);
      }

      return new AuthorizationDecision(
        ALLOWED_EMAILS.contains(email.toLowerCase())
      );
    };
  }
}