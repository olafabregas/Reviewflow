package com.reviewflow.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "course_instructors")
@IdClass(CourseInstructor.CourseInstructorId.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseInstructor {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CourseInstructorId implements java.io.Serializable {
        private Long course;  // matches Course.id
        private Long user;    // matches User.id
    }
}
