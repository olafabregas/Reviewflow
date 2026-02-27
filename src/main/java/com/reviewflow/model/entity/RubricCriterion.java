package com.reviewflow.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "rubric_criteria")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RubricCriterion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "max_score", nullable = false)
    private Integer maxScore;

    @Column(name = "display_order")
    @Builder.Default
    private Integer displayOrder = 0;
}
