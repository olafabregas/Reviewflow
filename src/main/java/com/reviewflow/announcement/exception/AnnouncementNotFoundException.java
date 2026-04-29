package com.reviewflow.announcement.exception;

import com.reviewflow.shared.exception.ResourceNotFoundException;

public class AnnouncementNotFoundException extends ResourceNotFoundException {

  public AnnouncementNotFoundException(String message) {
    super("Announcement", message);
  }

  public AnnouncementNotFoundException(Long announcementId) {
    super("Announcement", announcementId);
  }
}
