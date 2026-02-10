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
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

  private static final Set<String> ALLOWED_EMAILS = Set.of(
    "stepxstepclub@gmail.com",
    "ralphdarync@gmail.com"
  );

  // ✅ Your Auth0 Action claim
  private static final String EMAIL_CLAIM = "https://rddigitech.ca/email";

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
      .cors(Customizer.withDefaults())
      .csrf(csrf -> csrf.disable())
      .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

      .authorizeHttpRequests(auth -> auth
        // ✅ Allow preflight
        .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.OPTIONS, "/**")).permitAll()

        // ✅ Public health
        .requestMatchers(AntPathRequestMatcher.antMatcher("/api/health"))
        .permitAll()
        .requestMatchers(AntPathRequestMatcher.antMatcher("/api/health/**"))
        .permitAll()

        // ✅ TEMP DIAG: require ONLY a valid JWT for dashboard endpoints
        // If this becomes 200, switch back to the allowlist line below.
        .requestMatchers(AntPathRequestMatcher.antMatcher("/api/dashboard/**"))
        .authenticated()

        // ❗ Anything else blocked
        .anyRequest().denyAll()
      )

      .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))

      // ✅ Cleaner errors
      .exceptionHandling(eh -> eh
        .authenticationEntryPoint((req, res, ex) -> res.sendError(401, "Unauthorized"))
        .accessDeniedHandler((req, res, ex) -> res.sendError(403, "Forbidden"))
      );

    return http.build();
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
    config.setAllowCredentials(false);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }

  // ✅ Your allowlist gate (we will re-enable AFTER the diag works)
  @Bean
  AuthorizationManager<RequestAuthorizationContext> onlyAllowedEmails() {
    return (authenticationSupplier, context) -> {
      Authentication auth = authenticationSupplier.get();
      if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
        return new AuthorizationDecision(false);
      }

      Jwt jwt = jwtAuth.getToken();

      String email = jwt.getClaimAsString(EMAIL_CLAIM);
      if (email == null) {
        email = jwt.getClaimAsString("email");
      }
      if (email == null) return new AuthorizationDecision(false);

      return new AuthorizationDecision(ALLOWED_EMAILS.contains(email.toLowerCase()));
    };
  }

  /*
    ✅ AFTER you confirm ga4Results returns 200 with `.authenticated()`,
    change this line:

      .authenticated()

    to this:

      .access(onlyAllowedEmails())
  */
}