package com.reviewflow.event.email;

import lombok.Getter;

@Getter
public class AnnouncementPostedEmailEvent extends EmailEvent {

    private final String announcementTitle;
    private final String body;
    private final String senderName;
    private final String courseCode;

    public AnnouncementPostedEmailEvent(
            String recipientEmail,
            String recipientName,
            String announcementTitle,
            String body,
            String senderName,
            String courseCode) {
        super(recipientEmail, recipientName, EmailCategory.CRITICAL);
        this.announcementTitle = announcementTitle;
        this.body = body;
        this.senderName = senderName;
        this.courseCode = courseCode;
    }
}
