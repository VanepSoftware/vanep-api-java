package br.com.vanep.proposal.model;

import br.com.vanep.proposal.enums.ProposalAdjustmentField;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "proposal_adjustment")
@Getter
@Setter
public class ProposalAdjustmentModel {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "proposal_id", nullable = false, updatable = false)
  private Long proposalId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16, updatable = false)
  private ProposalAdjustmentField field;

  @Column(name = "suggested_value", length = 100, updatable = false)
  private String suggestedValue;

  @Column(length = 500, updatable = false)
  private String note;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
