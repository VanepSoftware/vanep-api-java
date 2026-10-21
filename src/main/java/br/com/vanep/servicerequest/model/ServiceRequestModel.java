package br.com.vanep.servicerequest.model;

import br.com.vanep.clientdriver.model.ClientDriverModel;
import br.com.vanep.servicerequest.enums.ServiceRequestStatus;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "service_request")
@Getter
@Setter
public class ServiceRequestModel {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 32)
  private String token;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "client_driver_id", nullable = false)
  private ClientDriverModel clientDriver;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private ServiceRequestStatus status;

  @Column(name = "desired_start_date", nullable = false)
  private LocalDate desiredStartDate;

  @Column(length = 1000)
  private String notes;

  @Column(name = "decline_reason", length = 500)
  private String declineReason;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  // A Set, not a List: Hibernate can fetch it together with each item's days.
  @OneToMany(mappedBy = "serviceRequest", cascade = CascadeType.ALL)
  private Set<ServiceRequestItemModel> items = new LinkedHashSet<>();

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public void addItem(ServiceRequestItemModel item) {
    item.setServiceRequest(this);
    items.add(item);
  }

  @PrePersist
  void onCreate() {
    if (token == null) {
      token = UUID.randomUUID().toString().replace("-", "").substring(0, 25);
    }
  }
}
