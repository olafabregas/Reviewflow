package com.reviewflow.infra.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  @Value("${server.port:8080}")
  private int serverPort;

  @Value("${swagger.prod-url:https://api.reviewflow.example.com}")
  private String prodApiUrl;

  /** Main OpenAPI bean - defines server URLs, security schemes, and component schemas */
  @Bean
  public OpenAPI openAPI() {
    final String securitySchemeName = "cookieAuth";
    return new OpenAPI()
        .info(
            new Info()
                .title("ReviewFlow API")
                .version("1.0.0")
                .description(
                    "ReviewFlow - Comprehensive Course Assignment & Evaluation Platform API\n\n"
                        + "**Base URL:** /api/v1\n\n"
                        + "**Authentication:** Cookie-based (reviewflow_access) via JWT token"))
        .servers(
            List.of(
                new Server()
                    .url("http://localhost:" + serverPort + "/api/v1")
                    .description("Local Development"),
                new Server().url(prodApiUrl + "/api/v1").description("Production")))
        .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
        .components(buildComponents(securitySchemeName));
  }

  /**
   * Public API group - includes public documentation endpoints, excludes admin/system This is used
   * by Redoc for public-facing documentation
   */
  @Bean
  public GroupedOpenApi publicApi() {
    return GroupedOpenApi.builder()
        .group("public")
        .displayName("Public API")
        .pathsToMatch("/**")
        .pathsToExclude("/admin/**", "/system/**")
        .build();
  }

  /** Admin API group - includes admin endpoints only (ADMIN role required) */
  @Bean
  public GroupedOpenApi adminApi() {
    return GroupedOpenApi.builder()
        .group("admin")
        .displayName("Admin API")
        .pathsToMatch("/admin/**")
        .build();
  }

  /** System API group - includes system administration endpoints (SYSTEM_ADMIN role required) */
  @Bean
  public GroupedOpenApi systemApi() {
    return GroupedOpenApi.builder()
        .group("system")
        .displayName("System Administration API")
        .pathsToMatch("/system/**")
        .build();
  }

  /** Build component schemas including reusable error responses and DTOs */
  private Components buildComponents(String securitySchemeName) {
    return new Components()
        .addSecuritySchemes(
            securitySchemeName,
            new SecurityScheme()
                .name(securitySchemeName)
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.COOKIE)
                .description(
                    "HTTP-only JWT cookie (name: reviewflow_access)\n\n"
                        + "Obtained via POST /auth/login and automatically sent with each request"))
        .addSchemas(
            "ErrorDetail",
            new Schema<>()
                .type("object")
                .description("Standard error response detail object")
                .addProperty(
                    "code",
                    new Schema<>()
                        .type("string")
                        .description(
                            "Machine-readable error code (e.g., 'VALIDATION_ERROR', 'NOT_FOUND')")
                        .example("NOT_FOUND"))
                .addProperty(
                    "message",
                    new Schema<>()
                        .type("string")
                        .description("Human-readable error message")
                        .example("The requested resource was not found"))
                .required(List.of("code", "message")))
        .addSchemas(
            "ApiErrorResponse",
            new Schema<>()
                .type("object")
                .description("Standard API error response envelope")
                .addProperty(
                    "success",
                    new Schema<>()
                        .type("boolean")
                        .description("Always false for error responses")
                        .example(false))
                .addProperty(
                    "data",
                    new Schema<>()
                        .type("object")
                        .nullable(true)
                        .description("Always null for error responses"))
                .addProperty("error", new Schema<>().$ref("#/components/schemas/ErrorDetail"))
                .addProperty(
                    "timestamp",
                    new Schema<>()
                        .type("string")
                        .format("date-time")
                        .description("ISO 8601 timestamp of the error")
                        .example("2026-03-15T10:30:00Z"))
                .required(List.of("success", "error", "timestamp")));
  }
}
