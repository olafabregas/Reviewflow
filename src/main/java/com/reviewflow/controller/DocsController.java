package com.reviewflow.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * DocsController — serves API documentation using Redoc
 * 
 * Provides HTML endpoints for viewing OpenAPI documentation:
 * - /docs — Public API documentation (Redoc)
 * - /docs/admin — Admin API documentation  
 * - /docs/system — System administration API documentation
 * - /swagger-ui.html — Interactive Swagger UI (auto-provided by SpringDoc)
 */
@Controller
@RequestMapping("/docs")
@Hidden  // Exclude from OpenAPI documentation
@Tag(name = "Documentation", description = "API documentation endpoints (Redoc)")
public class DocsController {
    /**
     * GET /docs — Serves Redoc documentation for public API
     * Displays only endpoints matching the "public" group (excludes /admin/**, /system/**)
     */
    @Operation(
        summary = "Public API documentation",
        description = "Serves interactive Redoc documentation for public API endpoints"
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Documentation HTML page"
        )
    })
    @GetMapping({"", "/"})
    @ResponseBody
    public String publicDocs() {
        return generateRedocHtml("Public API Documentation", "/api/v1/api-docs/public");
    }

    /**
     * GET /docs/admin — Serves Redoc documentation for admin endpoints
     * Displays only endpoints in /admin/** group
     */
    @Operation(
        summary = "Admin API documentation",
        description = "Serves interactive Redoc documentation for admin-only API endpoints"
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Documentation HTML page"
        )
    })
    @GetMapping("/admin")
    @ResponseBody
    public String adminDocs() {
        return generateRedocHtml("Admin API Documentation", "/api/v1/api-docs/admin");
    }

    /**
     * GET /docs/system — Serves Redoc documentation for system admin endpoints
     * Displays only endpoints in /system/** group
     */
    @Operation(
        summary = "System admin API documentation",
        description = "Serves interactive Redoc documentation for system administration API endpoints (SYSTEM_ADMIN only)"
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Documentation HTML page"
        )
    })
    @GetMapping("/system")
    @ResponseBody
    public String systemDocs() {
        return generateRedocHtml("System Administration API Documentation", "/api/v1/api-docs/system");
    }

    /**
     * Generates Redoc HTML page with the given title and OpenAPI spec URL
     * 
     * @param title the page title and heading
     * @param specUrl the URL to the OpenAPI specification JSON
     * @return HTML string with embedded Redoc
     */
    private String generateRedocHtml(String title, String specUrl) {
        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>%s - ReviewFlow API</title>
                    <meta charset="utf-8"/>
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <link href="https://fonts.googleapis.com/css?family=Montserrat:300,400,700|Roboto:300,400,700" rel="stylesheet">
                    <style>
                        body {
                            margin: 0;
                            padding: 0;
                            font-family: Roboto, sans-serif;
                            background-color: #f7f7f7;
                        }
                        redoc {
                            display: block;
                            max-width: 1600px;
                            margin: 0 auto;
                        }
                        redoc::part(navbar) {
                            background-color: #1a1a1a;
                        }
                        h1 {
                            margin-top: 0;
                            color: #1a1a1a;
                            text-align: center;
                            padding: 20px 0;
                            font-weight: 300;
                        }
                    </style>
                </head>
                <body>
                    <redoc spec-url='%s'></redoc>
                    <script src="https://cdn.jsdelivr.net/npm/redoc@latest/bundles/redoc.standalone.js"></script>
                </body>
                </html>
                """
                .formatted(title, specUrl);
    }
}
