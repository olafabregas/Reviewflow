package com.reviewflow.infra.security;

import com.reviewflow.infra.filter.ActuatorKeyAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * PRD-08: Machine Client Actuator Security Configuration Applies ONLY to /actuator/** endpoints for
 * machine clients using X-Actuator-Key header Runs at Order(1) before the main SecurityFilterChain
 * (Order(2))
 *
 * <p>Human users get Actuator security from main SecurityFilterChain (added later by PRD-09)
 */
@Configuration
@Order(1)
@RequiredArgsConstructor
public class ActuatorMachineClientSecurityConfig {

  private final ActuatorKeyAuthFilter actuatorKeyAuthFilter;

  @Bean
  SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
    http.securityMatcher("/actuator/**")
        .authorizeHttpRequests(
            authz ->
                authz
                    // Public health endpoint - no authentication required
                    .requestMatchers("/actuator/health")
                    .permitAll()
                    // Info endpoint - public for debugging
                    .requestMatchers("/actuator/info")
                    .permitAll()
                    // All other /actuator/* endpoints require X-Actuator-Key header
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(actuatorKeyAuthFilter, UsernamePasswordAuthenticationFilter.class)
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.disable())
        .httpBasic(httpBasic -> httpBasic.disable());

    return http.build();
  }
}
