package com.reviewflow.shared.domain;

import com.reviewflow.shared.domain.Evaluation;
    // TODO [STYLE-AGENT]: fix structural violation
import jakarta.persistence.*;
import java.math.BigDecimal; // TODO [STYLE-AGENT]: fix structural violation
    // TODO [STYLE-AGENT]: fix structural violation
import lombok.*;

@Entity
@Table(name = "rubric_scores")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RubricScore {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "evaluation_id", nullable = false)
  private Evaluation evaluation;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "criterion_id", nullable = false)
  private RubricCriterion criterion;

  @Column(precision = 5, scale = 2)
  private BigDecimal score;

  @Column(columnDefinition = "TEXT")
  private String comment;
}
