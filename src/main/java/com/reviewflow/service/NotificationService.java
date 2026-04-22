package com.reviewflow.service;
import com.reviewflow.util.HashidService;

import com.reviewflow.util.CacheNames;
import com.reviewflow.exception.AccessDeniedException;
import com.reviewflow.exception.ResourceNotFoundException;
import com.reviewflow.model.dto.response.NotificationDto;
import com.reviewflow.model.entity.Notification;
import com.reviewflow.model.enums.NotificationType;
import com.reviewflow.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final HashidService hashidService;

    @Transactional
    public Notification create(Long userId, NotificationType type, String title, String message, String actionUrl) {
        Notification n = Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .message(message)
                .actionUrl(actionUrl)
                .isRead(false)
                .build();
        return notificationRepository.save(n);
    }

    @CacheEvict(value = CacheNames.CACHE_UNREAD_COUNT, key = "#userId")
    @Transactional
    public void markAsRead(Long id, Long userId) {
        Notification notification = notificationRepository.findById(id)
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
        Notification notification = notificationRepository.findById(id)
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

    public Page<NotificationDto> getNotifications(Long userId, Boolean unreadOnly, Pageable pageable) {
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
