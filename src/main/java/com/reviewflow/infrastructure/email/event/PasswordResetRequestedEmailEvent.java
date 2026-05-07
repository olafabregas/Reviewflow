package com.reviewflow.infrastructure.email.event;

import lombok.Getter;

@Getter
public class PasswordResetRequestedEmailEvent extends EmailEvent {

  private final String firstName;
  private final String resetUrl;

  public PasswordResetRequestedEmailEvent(
      String recipientEmail, String firstName, String resetUrl) {
    super(recipientEmail, firstName, EmailCategory.CRITICAL);
    this.firstName = firstName;
    this.resetUrl = resetUrl;
  }
}
