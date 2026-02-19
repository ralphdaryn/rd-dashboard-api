package ca.rddigitech.rd_dashboard_api;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

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

  private static final String EMAIL_CLAIM = "https://rddigitech.ca/email";
  private static final String OWNER_EMAIL = "ralphdarync@gmail.com";

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    var ppm = PathPatternRequestMatcher.withDefaults();

    http
      .cors(Customizer.withDefaults())
      .csrf(csrf -> csrf.disable())
      .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

      .authorizeHttpRequests(auth -> auth
        .requestMatchers(ppm.matcher(HttpMethod.OPTIONS, "/**")).permitAll()

        // public
        .requestMatchers(ppm.matcher("/api/health")).permitAll()
        .requestMatchers(ppm.matcher("/api/health/**")).permitAll()

        // StepByStep
        .requestMatchers(ppm.matcher("/api/dashboard/stepbystep/**"))
          .access(onlyAllowedEmailsFromEnv("ALLOWED_EMAILS_STEPBYSTEP"))

        // KSnap Studio
        .requestMatchers(ppm.matcher("/api/dashboard/ksnapstudio/**"))
          .access(onlyAllowedEmailsFromEnv("ALLOWED_EMAILS_KSNAPSTUDIO"))

        // ✅ RD Digitech
        .requestMatchers(ppm.matcher("/api/dashboard/rddigitech/**"))
          .access(onlyAllowedEmailsFromEnv("ALLOWED_EMAILS_RDDIGITECH"))

        .anyRequest().denyAll()
      )

      .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))

      .exceptionHandling(eh -> eh
        .authenticationEntryPoint((req, res, ex) -> res.sendError(401, "Unauthorized"))
        .accessDeniedHandler((req, res, ex) -> res.sendError(403, "Forbidden"))
      );

    return http.build();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();

    List<String> origins = parseCsvEnvList("CORS_ALLOWED_ORIGINS");

    if (origins.isEmpty()) {
      origins = List.of(
        "https://stepbystepclub.ca",
        "https://www.stepbystepclub.ca",
        "https://ksnapstudio.ca",
        "https://www.ksnapstudio.ca",
        "https://rddigitech.ca",
        "https://www.rddigitech.ca",
        "http://localhost:5173",
        "http://localhost:5174",
        "http://localhost:8888"
      );
    }

    config.setAllowedOrigins(origins);
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    config.setExposedHeaders(List.of("Authorization"));
    config.setAllowCredentials(false);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }

  AuthorizationManager<RequestAuthorizationContext> onlyAllowedEmailsFromEnv(String envKey) {
    final Set<String> allow = new HashSet<>();

    allow.add(OWNER_EMAIL.toLowerCase(Locale.ROOT));

    for (String e : parseCsvEnvList(envKey)) {
      allow.add(e.toLowerCase(Locale.ROOT));
    }

    return (authenticationSupplier, context) -> {
      Authentication auth = authenticationSupplier.get();

      if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
        return new AuthorizationDecision(false);
      }

      Jwt jwt = jwtAuth.getToken();

      String email = jwt.getClaimAsString(EMAIL_CLAIM);
      if (email == null) email = jwt.getClaimAsString("email");
      if (email == null) return new AuthorizationDecision(false);

      return new AuthorizationDecision(allow.contains(email.toLowerCase(Locale.ROOT)));
    };
  }

  private static List<String> parseCsvEnvList(String key) {
    String raw = System.getenv(key);
    if (raw == null || raw.isBlank()) return List.of();

    return Arrays.stream(raw.split(","))
      .map(String::trim)
      .filter(s -> !s.isBlank())
      .collect(Collectors.toList());
  }
}