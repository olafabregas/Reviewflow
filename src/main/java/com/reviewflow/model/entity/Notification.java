package com.reviewflow.model.entity;

import com.reviewflow.model.enums.NotificationType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_notifications_user_id",    columnList = "user_id"),
    @Index(name = "idx_notifications_is_read",    columnList = "is_read"),
    @Index(name = "idx_notifications_created_at", columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Plain Long — no join needed, notifications just need to know who to send to
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationType type;

    @Column(nullable = false, length = 150)
    @Builder.Default
    private String title = "";

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    @Column(name = "action_url", length = 500)
    private String actionUrl;

    // Optional: ID of the resource this notification points to (team, submission, etc.)
    // Used for action URL rewriting with hashed IDs: "/teams/{id}" becomes "/teams/Xm2pNqR4"
    @Column(name = "target_id")
    private Long targetId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}