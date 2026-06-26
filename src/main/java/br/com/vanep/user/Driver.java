package br.com.vanep.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Perfil de motorista. 1:1 com {@link User}. Só recebe propostas após aprovação dos admins ({@link
 * DriverApprovalStatus#APPROVED}).
 */
@Entity
@Table(name = "driver")
@Getter
@Setter
public class Driver {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 32)
  private String token;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false, unique = true)
  private User user;

  @Column private String photo;

  @Column(precision = 3, scale = 2)
  private BigDecimal rating;

  @Column private String cnpj;

  @Column(name = "experience_years")
  private Integer experienceYears;

  @Column private String city;

  @Column(name = "base_price", nullable = false, precision = 12, scale = 2)
  private BigDecimal basePrice;

  @Enumerated(EnumType.STRING)
  @Column(name = "approval_status", nullable = false, length = 16)
  private DriverApprovalStatus approvalStatus = DriverApprovalStatus.PENDING;

  @Column(name = "is_active", nullable = false)
  private boolean active = true;

  @Column(name = "is_available", nullable = false)
  private boolean available = false;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @PrePersist
  void onCreate() {
    if (token == null) {
      token = UUID.randomUUID().toString().replace("-", "").substring(0, 25);
    }
  }
}
