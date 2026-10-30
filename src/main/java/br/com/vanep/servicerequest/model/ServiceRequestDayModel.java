package br.com.vanep.servicerequest.model;

import br.com.vanep.shared.enums.SchoolShift;
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
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "service_request_day")
@Getter
@Setter
public class ServiceRequestDayModel {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "service_request_item_id", nullable = false)
  private ServiceRequestItemModel serviceRequestItem;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private DayOfWeek weekday;

  @Enumerated(EnumType.STRING)
  @Column(name = "school_shift", nullable = false, length = 16)
  private SchoolShift schoolShift;

  @Column(name = "needs_outbound", nullable = false)
  private boolean needsOutbound;

  @Column(name = "needs_return", nullable = false)
  private boolean needsReturn;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
