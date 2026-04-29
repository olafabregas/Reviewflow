package com.reviewflow.shared.domain;

import com.reviewflow.model.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

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
    private Long course;
    private Long user;
  }
}
