package com.reviewflow.infrastructure.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

  private final JavaMailSender mailSender;

  @Value("${email.from}")
  private String fromAddress;

  public void send(String to, String subject, String htmlBody, String textBody) {
    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

      helper.setFrom(fromAddress);
      helper.setTo(to);
      helper.setSubject(subject);
      helper.setText(textBody, htmlBody);

      mailSender.send(message);
      log.debug("Email sent to {}: {}", to, subject);
    } catch (MessagingException e) {
      throw new org.springframework.mail.MailSendException("Failed to compose email", e);
    } catch (org.springframework.mail.MailException e) {
      log.warn("Email delivery failed for {} subject={}: {}", to, subject, e.getMessage());
    }
  }
}
