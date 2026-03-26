package com.reviewflow.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailService emailService;

    @Test
    void send_happyPath_buildsAndSendsMimeMessage() {
        ReflectionTestUtils.setField(emailService, "fromAddress", "no-reply@reviewflow.local");
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);

        emailService.send("student@test.local", "Welcome", "<p>Hello</p>", "Hello");

        verify(mailSender).send(message);
    }

    @Test
    void send_mailTransportFailure_isSwallowed() {
        ReflectionTestUtils.setField(emailService, "fromAddress", "no-reply@reviewflow.local");
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
        doThrow(new MailSendException("smtp unavailable")).when(mailSender).send(any(MimeMessage.class));

        assertDoesNotThrow(() -> emailService.send(
                "student@test.local",
                "Welcome",
                "<p>Hello</p>",
                "Hello"));

        verify(mailSender).send(message);
    }
}
