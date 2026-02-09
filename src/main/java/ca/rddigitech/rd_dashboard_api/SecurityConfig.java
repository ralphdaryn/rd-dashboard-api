package ca.rddigitech.rd_dashboard_api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
      .csrf(csrf -> csrf.disable())
      // Stateless API — no sessions, no JSESSIONID
      .sessionManagement(sm ->
        sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
      )
      .authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/health", "/api/health/**").permitAll()
        .requestMatchers("/api/dashboard/**").authenticated()
        .anyRequest().denyAll()
      )
      .oauth2ResourceServer(oauth2 ->
        oauth2.jwt(jwt -> {})
      );

    return http.build();
  }
}