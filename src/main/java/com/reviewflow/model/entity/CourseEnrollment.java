package com.reviewflow.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "course_enrollments")
@IdClass(CourseEnrollment.CourseEnrollmentId.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseEnrollment {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "enrolled_at")
    private Instant enrolledAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CourseEnrollmentId implements java.io.Serializable {
        private Long course;
        private Long user;
    }
}
