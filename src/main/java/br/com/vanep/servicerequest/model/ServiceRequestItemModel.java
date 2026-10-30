package br.com.vanep.servicerequest.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "service_request_item")
@Getter
@Setter
public class ServiceRequestItemModel {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "service_request_id", nullable = false)
  private ServiceRequestModel serviceRequest;

  // Plain ids, not associations: Hibernate hides a soft-deleted row in every join, which would
  // hide the request. The screens read the snapshot; the ids are for navigation and re-checks.
  @Column(name = "dependent_id", nullable = false, updatable = false)
  private Long dependentId;

  @Column(name = "school_id", nullable = false, updatable = false)
  private Long schoolId;

  @Column(name = "pickup_city_id", nullable = false, updatable = false)
  private Long pickupCityId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
  private Snapshot snapshot;

  @OneToMany(mappedBy = "serviceRequestItem", cascade = CascadeType.ALL)
  private Set<ServiceRequestDayModel> days = new LinkedHashSet<>();

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public void addDay(ServiceRequestDayModel day) {
    day.setServiceRequestItem(this);
    days.add(day);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Snapshot(int version, Dependent dependent, School school, Pickup pickup) {
    public static final int CURRENT_VERSION = 1;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Dependent(String name, LocalDate birthDate) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record School(String name) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Pickup(String cityName, String stateUf, String neighborhood) {}
  }
}
