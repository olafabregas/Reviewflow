package com.reviewflow.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.reviewflow.security.JwtAuthenticationFilter;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${app.cors.allowed-origins:http://localhost:5173}")
    private String allowedOriginsConfig;

    @Value("${spring.profiles.active:local}")
    private String activeProfile;

    /**
     * PRD-09: Role hierarchy — Roles follow Spring Security conventions:
     * SYSTEM_ADMIN > ADMIN > INSTRUCTOR > STUDENT
     *
     * Permission Model: - Use @PreAuthorize("hasAnyRole('ADMIN',
     * 'SYSTEM_ADMIN')") for endpoints requiring ADMIN or higher - Use
     * @PreAuthorize("hasRole('INSTRUCTOR')") for INSTRUCTOR+ (auto-includes
     * ADMIN, SYSTEM_ADMIN via hasAnyRole patterns) - Use
     * @PreAuthorize("isAuthenticated()") for STUDENT+ (service-layer validates
     * ownership)
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session
                        -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // Security Headers
                .headers(headers -> headers
                // Prevents browsers from MIME-sniffing a response away from declared content-type
                .contentTypeOptions(contentTypeOptions -> contentTypeOptions.disable())
                .xssProtection(xss -> xss
                .headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                // Prevents rendering in <frame>, <iframe>, <embed>, or <object>
                .frameOptions(frameOptions -> frameOptions.deny())
                // Controls how much referrer information is included with requests
                .referrerPolicy(referrer -> referrer
                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                // HSTS - Force HTTPS for one year (disabled in local/dev to prevent localhost breakage)
                .httpStrictTransportSecurity(hsts -> {
                    if ("prod".equals(activeProfile)) {
                        hsts.includeSubDomains(true).maxAgeInSeconds(31536000);
                    } else {
                        hsts.maxAgeInSeconds(0); // disable HSTS in local/dev
                    }
                })
                // Content Security Policy — cdnjs and jsdelivr are allowlisted because Redoc and
                // Swagger UI load their assets from those CDNs. unsafe-inline is required by the
                // Swagger renderer's inline style injection and cannot be removed without breaking the UI.
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                "default-src 'self'; "
                + "script-src 'self' 'unsafe-inline' https://cdnjs.cloudflare.com https://cdn.jsdelivr.net; "
                + "style-src 'self' 'unsafe-inline' https://cdnjs.cloudflare.com https://cdn.jsdelivr.net; "
                + "img-src 'self' data: https:; "
                + "font-src 'self' data: https://cdnjs.cloudflare.com; "
                + "connect-src 'self' https://cdnjs.cloudflare.com; "
                + "frame-ancestors 'none'"
        ))
                )
                .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/docs").permitAll() // Public API documentation
                .requestMatchers("/docs/admin").hasAnyRole("ADMIN", "SYSTEM_ADMIN") // Admin docs accessible to ADMIN and SYSTEM_ADMIN
                .requestMatchers("/docs/system").hasRole("SYSTEM_ADMIN") // System docs for SYSTEM_ADMIN only
                .requestMatchers("/api/v1/api-docs/**").permitAll() // OpenAPI specs - public access
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/ws/**").permitAll() // WebSocket endpoint
                // PRD-09: System admin endpoints
                .requestMatchers("/system/**").hasRole("SYSTEM_ADMIN")
                .requestMatchers("/users/*/force-logout").hasRole("SYSTEM_ADMIN")
                .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
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
        DefaultMethodSecurityExpressionHandler expressionHandler = new DefaultMethodSecurityExpressionHandler();
        return expressionHandler;
    }
}
