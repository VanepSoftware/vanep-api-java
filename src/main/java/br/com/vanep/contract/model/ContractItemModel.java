package br.com.vanep.contract.model;

import br.com.vanep.city.model.CityModel;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.district.model.DistrictModel;
import br.com.vanep.schedule.model.ScheduleModel;
import br.com.vanep.school.model.SchoolModel;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.SoftDeleteType;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "contract_item")
@SoftDelete(columnName = "deleted_at", strategy = SoftDeleteType.TIMESTAMP)
@Getter
@Setter
public class ContractItemModel {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 32)
  private String token;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "contract_id", nullable = false)
  private ContractModel contract;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "dependent_id", nullable = false)
  private DependentModel dependent;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "school_id", nullable = false)
  private SchoolModel school;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "pickup_city_id", nullable = false)
  private CityModel pickupCity;

  @Column(name = "pickup_zip_code", length = 8)
  private String pickupZipCode;

  @Column(name = "pickup_street", nullable = false)
  private String pickupStreet;

  @Column(name = "pickup_number", length = 16)
  private String pickupNumber;

  @Column(name = "pickup_complement", length = 128)
  private String pickupComplement;

  @Column(name = "pickup_neighborhood", length = 128)
  private String pickupNeighborhood;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "pickup_district_id")
  private DistrictModel pickupDistrict;

  @Column(name = "pickup_google_place_id")
  private String pickupGooglePlaceId;

  @Column(name = "monthly_amount", nullable = false, precision = 12, scale = 2)
  private BigDecimal monthlyAmount;

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
