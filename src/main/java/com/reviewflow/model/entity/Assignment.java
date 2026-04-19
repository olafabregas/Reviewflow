package com.reviewflow.model.entity;

import com.reviewflow.model.enums.SubmissionType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "assignments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private AssignmentGroup assignmentGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "module_id", nullable = true)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private AssignmentModule assignmentModule;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "max_team_size")
    private Integer maxTeamSize;

    @Column(name = "max_score", precision = 6, scale = 2)
    private BigDecimal maxScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "submission_type", nullable = false)
    @Builder.Default
    private SubmissionType submissionType = SubmissionType.INDIVIDUAL;

    @Column(name = "is_published", nullable = false)
    @Builder.Default
    private Boolean isPublished = false;

    @Column(name = "team_lock_at")
    private Instant teamLockAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @OneToMany(mappedBy = "assignment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<RubricCriterion> rubricCriteria = new ArrayList<>();
}
