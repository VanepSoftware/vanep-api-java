package br.com.vanep.school.model;

import br.com.vanep.city.model.CityModel;
import br.com.vanep.district.model.DistrictModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "school")
@SoftDelete(columnName = "deleted_at", strategy = SoftDeleteType.TIMESTAMP)
@Getter
@Setter
public class SchoolModel {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 32)
  private String token;

  @Column(nullable = false, length = 255)
  private String name;

  @Column(name = "google_place_id", length = 255)
  private String googlePlaceId;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "city_id")
  private CityModel city;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "district_id")
  private DistrictModel district;

  @Column(name = "address_id")
  private Long addressId;

  @Column(name = "is_active", nullable = false)
  private boolean active = true;

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
