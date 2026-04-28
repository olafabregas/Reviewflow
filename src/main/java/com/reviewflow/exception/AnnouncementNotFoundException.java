package com.reviewflow.exception;

/**
 * AnnouncementNotFoundException — thrown when announcement is not found or not visible to actor.
 * HTTP 404.
 */
public class AnnouncementNotFoundException extends ResourceNotFoundException {

  public AnnouncementNotFoundException(String message) {
    super("Announcement", message);
  }

  public AnnouncementNotFoundException(Long announcementId) {
    super("Announcement", announcementId);
  }
}
