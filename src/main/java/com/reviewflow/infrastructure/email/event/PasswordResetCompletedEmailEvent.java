package com.reviewflow.infrastructure.email.event;

import lombok.Getter;

@Getter
public class PasswordResetCompletedEmailEvent extends EmailEvent {

  private final String firstName;

  public PasswordResetCompletedEmailEvent(String recipientEmail, String firstName) {
    super(recipientEmail, firstName, EmailCategory.STANDARD);
    this.firstName = firstName;
  }
}
