package com.reviewflow.event.email;

import lombok.Getter;

@Getter
public class AccountReactivatedEmailEvent extends EmailEvent {

    private final String firstName;

    public AccountReactivatedEmailEvent(String recipientEmail, String firstName) {
        super(recipientEmail, firstName, EmailCategory.CRITICAL);
        this.firstName = firstName;
    }
}
