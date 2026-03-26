package com.reviewflow.event.email;

import lombok.Getter;

@Getter
public abstract class EmailEvent {

    public enum EmailCategory {
        CRITICAL,
        STANDARD
    }

    private final String recipientEmail;
    private final String recipientName;
    private final EmailCategory category;

    protected EmailEvent(String recipientEmail, String recipientName, EmailCategory category) {
        this.recipientEmail = recipientEmail;
        this.recipientName = recipientName;
        this.category = category;
    }
}
