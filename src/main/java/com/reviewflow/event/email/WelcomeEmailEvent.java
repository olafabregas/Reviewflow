package com.reviewflow.event.email;

import lombok.Getter;

@Getter
public class WelcomeEmailEvent extends EmailEvent {

    private final Long userId;
    private final String firstName;

    public WelcomeEmailEvent(Long userId, String recipientEmail, String firstName) {
        super(recipientEmail, firstName, EmailCategory.CRITICAL);
        this.userId = userId;
        this.firstName = firstName;
    }
}
