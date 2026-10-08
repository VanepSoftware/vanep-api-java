package br.com.vanep.stopchange.model;

import br.com.vanep.address.model.AddressModel;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.stopchange.enums.StopChangeStatus;
import br.com.vanep.trip.model.TripModel;
import br.com.vanep.user.model.UserModel;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.SoftDeleteType;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "stop_change_request")
@SoftDelete(columnName = "deleted_at", strategy = SoftDeleteType.TIMESTAMP)
@Getter
@Setter
public class StopChangeRequestModel {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 32)
  private String token;

  @Column(name = "contract_id")
  private Long contractId;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "dependent_id", nullable = false)
  private DependentModel dependent;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "trip_id", nullable = false)
  private TripModel trip;

  @Column(name = "service_date", nullable = false)
  private LocalDate serviceDate;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "requested_by_user_id", nullable = false)
  private UserModel requestedByUser;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "new_dropoff_address_id", nullable = false)
  private AddressModel newDropoffAddress;

  @Column(length = 255)
  private String reason;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private StopChangeStatus status = StopChangeStatus.PENDING;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "responded_by_user_id")
  private UserModel respondedByUser;

  @Column(name = "responded_at")
  private Instant respondedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void onCreate() {
    if (token == null) {
      token = UUID.randomUUID().toString().replace("-", "").substring(0, 25);
    }
  }
}
