package br.com.vanep.absence.model;

import br.com.vanep.absence.enums.AbsenceLeg;
import br.com.vanep.absence.enums.AbsenceSource;
import br.com.vanep.clientdriver.model.ClientDriverModel;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.trip.model.TripModel;
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
@Table(name = "absence")
@SoftDelete(columnName = "deleted_at", strategy = SoftDeleteType.TIMESTAMP)
@Getter
@Setter
public class AbsenceModel {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 32)
  private String token;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "client_driver_id", nullable = false)
  private ClientDriverModel clientDriver;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "dependent_id", nullable = false)
  private DependentModel dependent;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "trip_id")
  private TripModel trip;

  @Column(name = "absence_date", nullable = false)
  private LocalDate absenceDate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private AbsenceLeg leg;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private AbsenceSource source;

  @Column(length = 255)
  private String reason;

  @Column(name = "notified_at")
  private Instant notifiedAt;

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
