package com.reviewflow.notification.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reviewflow.notification.dto.response.NotificationDto;
import com.reviewflow.notification.repository.NotificationRepository;
import com.reviewflow.shared.constant.CacheNames;
import com.reviewflow.shared.domain.Notification;
import com.reviewflow.shared.domain.NotificationType;
import com.reviewflow.shared.exception.AccessDeniedException;
import com.reviewflow.shared.exception.ResourceNotFoundException;
import com.reviewflow.shared.util.HashidService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationService {

  private final NotificationRepository notificationRepository;
  private final HashidService hashidService;

  @Transactional
  public Notification create(
      Long userId, NotificationType type, String title, String message, String actionUrl) {
    Notification n =
        Notification.builder()
            .userId(userId)
            .type(type)
            .title(title)
            .message(message)
            .actionUrl(actionUrl)
            .isRead(false)
            .build();
    return notificationRepository.save(n);
  }

  /**
   * PRD-17: idempotent per (user, discussion, UTC calendar day) via {@code uk_notification_dedup}.
   *
   * @return the saved row if inserted; empty if duplicate (already reminded today).
   */
  @Transactional
  @CacheEvict(value = CacheNames.CACHE_UNREAD_COUNT, key = "#userId")
  public Optional<Notification> tryCreateDedupedDiscussionReminder(
      Long userId, Long discussionId, String title, String message, String actionUrl) {
    Notification n =
        Notification.builder()
            .userId(userId)
            .type(NotificationType.DISCUSSION_REMINDER)
            .title(title)
            .message(message)
            .actionUrl(actionUrl)
            .targetId(discussionId)
            .dateBucket(LocalDate.now(ZoneOffset.UTC))
            .isRead(false)
            .build();
    try {
      Notification saved = notificationRepository.save(n);
      notificationRepository.flush();
      return Optional.of(saved);
    } catch (DataIntegrityViolationException ex) {
      return Optional.empty();
    }
  }

  @CacheEvict(value = CacheNames.CACHE_UNREAD_COUNT, key = "#userId")
  @Transactional
  public void markAsRead(Long id, Long userId) {
    Notification notification =
        notificationRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Notification", id));

    if (!notification.getUserId().equals(userId)) {
      throw new AccessDeniedException("Not authorized to access this notification");
    }

    notification.setIsRead(true);
    notificationRepository.save(notification);
  }

  @CacheEvict(value = CacheNames.CACHE_UNREAD_COUNT, key = "#userId")
  @Transactional
  public int markAllAsRead(Long userId) {
    return notificationRepository.markAllReadByUserId(userId);
  }

  @Transactional
  public void deleteNotification(Long id, Long userId) {
    Notification notification =
        notificationRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Notification", id));

    if (!notification.getUserId().equals(userId)) {
      throw new AccessDeniedException("Not authorized to delete this notification");
    }

    notificationRepository.deleteById(id);
  }

  @Cacheable(value = CacheNames.CACHE_UNREAD_COUNT, key = "#userId")
  @Transactional(readOnly = true)
  public long getUnreadCount(Long userId) {
    return notificationRepository.countByUserIdAndIsReadFalse(userId);
  }

  public Page<NotificationDto> getNotifications(
      Long userId, Boolean unreadOnly, Pageable pageable) {
    if (Boolean.TRUE.equals(unreadOnly)) {
      return notificationRepository
          .findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId, pageable)
          .map(n -> NotificationDto.from(n, hashidService));
    }
    return notificationRepository
        .findByUserIdOrderByCreatedAtDesc(userId, pageable)
        .map(n -> NotificationDto.from(n, hashidService));
  }

  @Transactional
  public void deleteOlderThan(Instant cutoff) {
    notificationRepository.deleteOlderThan(cutoff);
  }
}
