package br.com.vanep.proposal.model;

import br.com.vanep.schedule.model.ScheduleModel;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "proposal_item")
@Getter
@Setter
public class ProposalItemModel {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "proposal_id", nullable = false, updatable = false)
  private ProposalModel proposal;

  // A plain column for the same reason as in service_request_item: the name shown comes from the
  // request item copy, and a removed dependent must not hide the proposal.
  @Column(name = "dependent_id", nullable = false, updatable = false)
  private Long dependentId;

  @Column(name = "monthly_amount", nullable = false, precision = 12, scale = 2, updatable = false)
  private BigDecimal monthlyAmount;

  @OneToOne(fetch = FetchType.EAGER, optional = false, cascade = CascadeType.ALL)
  @JoinColumn(name = "schedule_id", nullable = false, updatable = false)
  private ScheduleModel schedule;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
