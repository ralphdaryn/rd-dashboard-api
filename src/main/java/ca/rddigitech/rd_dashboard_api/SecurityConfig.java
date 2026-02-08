package ca.rddigitech.rd_dashboard_api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
      .csrf(csrf -> csrf.disable())
      .authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/health").permitAll()
        .requestMatchers("/api/dashboard/**").authenticated()
        .anyRequest().denyAll()
      )
      // For Phase B, we’re just proving "public vs protected".
      // OAuth/JWT comes in Phase C.
      .httpBasic(basic -> {});

    return http.build();
  }
}