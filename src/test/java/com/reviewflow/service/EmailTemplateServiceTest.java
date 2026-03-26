package com.reviewflow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

@ExtendWith(MockitoExtension.class)
class EmailTemplateServiceTest {

    @Mock
    private SpringTemplateEngine templateEngine;

    @InjectMocks
    private EmailTemplateService templateService;

    @Test
    void renderHtml_usesEmailTemplatePrefix() {
        when(templateEngine.process(eq("email/welcome"), any(Context.class))).thenReturn("<h1>Welcome</h1>");

        String rendered = templateService.renderHtml("welcome", Map.of("firstName", "Ada"));

        assertEquals("<h1>Welcome</h1>", rendered);
        verify(templateEngine).process(eq("email/welcome"), any(Context.class));
    }

    @Test
    void renderText_usesTextTemplatePrefix() {
        when(templateEngine.process(eq("email/text/welcome"), any(Context.class))).thenReturn("Welcome Ada");

        String rendered = templateService.renderText("welcome", Map.of("firstName", "Ada"));

        assertEquals("Welcome Ada", rendered);
        verify(templateEngine).process(eq("email/text/welcome"), any(Context.class));
    }
}
