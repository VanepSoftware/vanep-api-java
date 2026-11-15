package br.com.vanep.proposal.model;

import br.com.vanep.clientdriver.model.ClientDriverModel;
import br.com.vanep.proposal.enums.ProposalAdjustmentPolicy;
import br.com.vanep.proposal.enums.ProposalCancellationPolicy;
import br.com.vanep.proposal.enums.ProposalStatus;
import br.com.vanep.servicerequest.model.ServiceRequestModel;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "proposal")
@Getter
@Setter
public class ProposalModel {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 32, updatable = false)
  private String token;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "client_driver_id", nullable = false, updatable = false)
  private ClientDriverModel clientDriver;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "service_request_id", nullable = false, updatable = false)
  private ServiceRequestModel serviceRequest;

  @Column(nullable = false, updatable = false)
  private Integer version;

  @Column(name = "parent_proposal_id", updatable = false)
  private Long parentProposalId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private ProposalStatus status;

  @Column(name = "valid_until", nullable = false, updatable = false)
  private Instant validUntil;

  @Column(name = "starts_on", nullable = false, updatable = false)
  private LocalDate startsOn;

  @Column(name = "ends_on", nullable = false, updatable = false)
  private LocalDate endsOn;

  @Column(name = "total_amount", nullable = false, precision = 12, scale = 2, updatable = false)
  private BigDecimal totalAmount;

  @Column(nullable = false, updatable = false)
  private Integer installments;

  @Column(name = "due_day", nullable = false, updatable = false)
  private Integer dueDay;

  @Column(name = "enrollment_fee", precision = 12, scale = 2, updatable = false)
  private BigDecimal enrollmentFee;

  @Enumerated(EnumType.STRING)
  @Column(name = "adjustment_policy", nullable = false, length = 32, updatable = false)
  private ProposalAdjustmentPolicy adjustmentPolicy;

  @Enumerated(EnumType.STRING)
  @Column(name = "cancellation_policy", nullable = false, length = 32, updatable = false)
  private ProposalCancellationPolicy cancellationPolicy;

  // Plain ids, not associations: Hibernate hides a soft-deleted row in every join, and the driver
  // can edit, remove and restore the vehicle. The screens read the vehicle from the snapshot.
  @Column(name = "vehicle_id", nullable = false, updatable = false)
  private Long vehicleId;

  @Column(length = 1000, updatable = false)
  private String message;

  @Column(name = "decline_reason", length = 500)
  private String declineReason;

  @Column(name = "accepted_at")
  private Instant acceptedAt;

  @Column(name = "source_contract_id", updatable = false)
  private Long sourceContractId;

  @Column(name = "resulting_contract_id")
  private Long resultingContractId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
  private Snapshot snapshot;

  // A Set, not a List: Hibernate can fetch it together with each item's slot list.
  @OneToMany(mappedBy = "proposal", cascade = CascadeType.ALL)
  private Set<ProposalItemModel> items = new LinkedHashSet<>();

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public void addItem(ProposalItemModel item) {
    item.setProposal(this);
    items.add(item);
  }

  @PrePersist
  void onCreate() {
    if (token == null) {
      token = UUID.randomUUID().toString().replace("-", "").substring(0, 25);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Snapshot(int version, Vehicle vehicle) {
    public static final int CURRENT_VERSION = 1;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Vehicle(
        String plate,
        String brand,
        String model,
        Integer manufactureYear,
        String color,
        Integer capacity) {}
  }
}
