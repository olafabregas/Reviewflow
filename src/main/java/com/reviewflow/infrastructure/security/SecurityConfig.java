package com.reviewflow.infrastructure.security;

import com.reviewflow.infrastructure.ratelimit.RateLimitFilter;
import com.reviewflow.infrastructure.security.JwtAuthenticationFilter;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final RateLimitFilter rateLimitFilter;
  private final HttpErrorJsonWriter httpErrorJsonWriter;

  @Value("${app.cors.allowed-origins:http://localhost:5173}")
  private String allowedOriginsConfig;

  @Value("${spring.profiles.active:local}")
  private String activeProfile;

  /**
   * PRD-09: Role hierarchy — Roles follow Spring Security conventions: SYSTEM_ADMIN > ADMIN >
   * INSTRUCTOR > STUDENT
   *
   * <p>Permission Model: - Use @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')") for endpoints
   * requiring ADMIN or higher - Use @PreAuthorize("hasRole('INSTRUCTOR')") for INSTRUCTOR+
   * (auto-includes ADMIN, SYSTEM_ADMIN via hasAnyRole patterns) -
   * Use @PreAuthorize("isAuthenticated()") for STUDENT+ (service-layer validates ownership)
   */
  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        // Security Headers
        .headers(
            headers ->
                headers
                    // Prevents browsers from MIME-sniffing a response away from declared
                    // content-type
                    .contentTypeOptions(contentTypeOptions -> contentTypeOptions.disable())
                    .xssProtection(
                        xss ->
                            xss.headerValue(
                                XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                    // Prevents rendering in <frame>, <iframe>, <embed>, or <object>
                    .frameOptions(frameOptions -> frameOptions.deny())
                    // Controls how much referrer information is included with requests
                    .referrerPolicy(
                        referrer ->
                            referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy
                                    .STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                    // HSTS - Force HTTPS for one year (disabled in local/dev to prevent localhost
                    // breakage)
                    .httpStrictTransportSecurity(
                        hsts -> {
                          if ("prod".equals(activeProfile)) {
                            hsts.includeSubDomains(true).maxAgeInSeconds(31536000);
                          } else {
                            hsts.maxAgeInSeconds(0); // disable HSTS in local/dev
                          }
                        })
                    // Content Security Policy - restrict resource loading
                    // Allows CDN resources (cdnjs, jsdelivr) for Redoc and Swagger UI
                    // while maintaining security restrictions for script and style
                    .contentSecurityPolicy(
                        csp ->
                            csp.policyDirectives(
                                "default-src 'self'; script-src 'self' 'unsafe-inline'"
                                    + " https://cdnjs.cloudflare.com https://cdn.jsdelivr.net;"
                                    + " style-src 'self' 'unsafe-inline'"
                                    + " https://cdnjs.cloudflare.com https://cdn.jsdelivr.net;"
                                    + " img-src 'self' data: https:; font-src 'self' data:"
                                    + " https://cdnjs.cloudflare.com; connect-src 'self'"
                                    + " https://cdnjs.cloudflare.com; frame-ancestors 'none'")))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.POST, "/api/v1/auth/login")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/password-reset/request")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/password-reset/confirm")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/auth/token")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/auth/ws-ticket")
                    .authenticated()
                    .requestMatchers("/api/v1/auth/sessions/**")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout")
                    .authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/v1/auth/me")
                    .authenticated()
                    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**")
                    .permitAll()
                    .requestMatchers("/docs")
                    .permitAll() // Public API documentation
                    .requestMatchers("/docs/admin")
                    .hasAnyRole(
                        "ADMIN", "SYSTEM_ADMIN") // Admin docs accessible to ADMIN and SYSTEM_ADMIN
                    .requestMatchers("/docs/system")
                    .hasRole("SYSTEM_ADMIN") // System docs for SYSTEM_ADMIN only
                    .requestMatchers("/api/v1/api-docs/**")
                    .permitAll() // OpenAPI specs - public access
                    .requestMatchers("/actuator/health")
                    .permitAll()
                    .requestMatchers("/ws/**")
                    .permitAll() // WebSocket endpoint
                    // PRD-09: System admin endpoints (includes POST /system/users/{id}/force-logout)
                    .requestMatchers("/system/**")
                    .hasRole("SYSTEM_ADMIN")
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(
                    (request, response, authException) ->
                        httpErrorJsonWriter.writeError(
                            response,
                            HttpStatus.UNAUTHORIZED.value(),
                            "UNAUTHORIZED",
                            "Authentication required")))
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class);
    return http.build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(Arrays.asList(allowedOriginsConfig.split("\\s*,\\s*")));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }

  @Bean
  public MethodSecurityExpressionHandler methodSecurityExpressionHandler() {
    DefaultMethodSecurityExpressionHandler expressionHandler =
        new DefaultMethodSecurityExpressionHandler();
    return expressionHandler;
  }
}
