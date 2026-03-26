package com.reviewflow.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templatemode.TemplateMode;

class EmailTemplateCatalogTest {

    private EmailTemplateService templateService;

    @BeforeEach
    void setUp() {
        SpringTemplateEngine templateEngine = new SpringTemplateEngine();

        ClassLoaderTemplateResolver htmlResolver = new ClassLoaderTemplateResolver();
        htmlResolver.setPrefix("templates/");
        htmlResolver.setSuffix(".html");
        htmlResolver.setTemplateMode(TemplateMode.HTML);
        htmlResolver.setCharacterEncoding("UTF-8");
        htmlResolver.setCheckExistence(true);
        htmlResolver.setOrder(1);
        htmlResolver.setResolvablePatterns(java.util.Set.of("email/*"));

        ClassLoaderTemplateResolver textResolver = new ClassLoaderTemplateResolver();
        textResolver.setPrefix("templates/");
        textResolver.setSuffix(".html");
        textResolver.setTemplateMode(TemplateMode.TEXT);
        textResolver.setCharacterEncoding("UTF-8");
        textResolver.setCheckExistence(true);
        textResolver.setOrder(2);
        textResolver.setResolvablePatterns(java.util.Set.of("email/text/*"));

        templateEngine.addTemplateResolver(htmlResolver);
        templateEngine.addTemplateResolver(textResolver);
        templateService = new EmailTemplateService(templateEngine);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "welcome",
            "evaluation-published",
            "submission-received",
            "team-invite-received",
            "team-invite-responded",
            "assignment-due-soon",
            "announcement-posted",
            "extension-request-received",
            "extension-decision",
            "account-reactivated"
    })
    void renderHtml_templatesExistAndRender(String templateName) {
        String rendered = templateService.renderHtml(templateName, baseVariables());

        assertNotNull(rendered);
        assertFalse(rendered.isBlank());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "welcome",
            "evaluation-published",
            "submission-received",
            "team-invite-received",
            "team-invite-responded",
            "assignment-due-soon",
            "announcement-posted",
            "extension-request-received",
            "extension-decision",
            "account-reactivated"
    })
    void renderText_templatesExistAndRender(String templateName) {
        String rendered = templateService.renderText(templateName, baseVariables());

        assertNotNull(rendered);
        assertFalse(rendered.isBlank());
    }

    private Map<String, Object> baseVariables() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("firstName", "Ada");
        vars.put("recipientName", "Ada Lovelace");
        vars.put("assignmentTitle", "Project 1");
        vars.put("courseCode", "CSC101");
        vars.put("totalScore", 92);
        vars.put("evaluationHashId", "EVAL123");
        vars.put("instructorName", "Instructor Jane");
        vars.put("submitterName", "Team Alpha");
        vars.put("versionNumber", 2);
        vars.put("submissionHashId", "SUB123");
        vars.put("inviterName", "John Doe");
        vars.put("teamName", "Team Delta");
        vars.put("teamMemberHashId", "TM123");
        vars.put("inviteeName", "Grace Hopper");
        vars.put("accepted", true);
        vars.put("dueAt", Instant.parse("2026-03-30T12:00:00Z"));
        vars.put("assignmentHashId", "ASSIGN123");
        vars.put("announcementTitle", "Deadline Update");
        vars.put("body", "Please review the updated rubric.");
        vars.put("senderName", "Prof. Smith");
        vars.put("studentName", "Alan Turing");
        vars.put("requestedDueAt", Instant.parse("2026-04-01T12:00:00Z"));
        vars.put("reason", "Medical appointment");
        vars.put("extensionRequestHashId", "ER123");
        vars.put("approved", true);
        vars.put("instructorNote", "Approved due to valid documentation.");
        vars.put("newDueAt", Instant.parse("2026-04-03T12:00:00Z"));
        vars.put("appBaseUrl", "http://localhost:5173");
        vars.put("preferencesUrl", "http://localhost:5173/settings/preferences");
        return vars;
    }
}
