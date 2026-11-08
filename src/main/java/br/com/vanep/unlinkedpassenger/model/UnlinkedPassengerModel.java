package br.com.vanep.unlinkedpassenger.model;

import br.com.vanep.address.model.AddressModel;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.schedule.model.ScheduleModel;
import br.com.vanep.school.model.SchoolModel;
import br.com.vanep.shared.enums.SchoolShift;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.SoftDeleteType;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "unlinked_passenger")
@SoftDelete(columnName = "deleted_at", strategy = SoftDeleteType.TIMESTAMP)
@Getter
@Setter
public class UnlinkedPassengerModel {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 32)
  private String token;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "driver_id", nullable = false)
  private DriverModel driver;

  @Column(nullable = false)
  private String name;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "school_id", nullable = false)
  private SchoolModel school;

  @Enumerated(EnumType.STRING)
  @Column(name = "school_shift", nullable = false, length = 16)
  private SchoolShift schoolShift;

  // Many-to-one on purpose: a one-to-one would add a full unique constraint, while the
  // single-owner rule is a partial index that frees the address after a soft delete.
  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "address_id", nullable = false)
  private AddressModel address;

  @Column(length = 500)
  private String notes;

  @OneToOne(fetch = FetchType.EAGER, optional = false, cascade = CascadeType.ALL)
  @JoinColumn(name = "schedule_id", nullable = false)
  private ScheduleModel schedule;

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
