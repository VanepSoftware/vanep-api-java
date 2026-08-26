package br.com.vanep.driverdocument.model;

import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.driverdocument.enums.DocumentStatusEnum;
import br.com.vanep.driverdocument.enums.DocumentTypeEnum;
import br.com.vanep.driverdocument.enums.ReviewMethodEnum;
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
@Table(name = "driver_document")
@SoftDelete(columnName = "deleted_at", strategy = SoftDeleteType.TIMESTAMP)
@Getter
@Setter
public class DriverDocumentModel {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 32)
  private String token;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "driver_id", nullable = false)
  private DriverModel driver;

  @Enumerated(EnumType.STRING)
  @Column(name = "document_type", nullable = false, length = 50)
  private DocumentTypeEnum documentType;

  @Column(name = "file_url", nullable = false, length = 512)
  private String fileUrl;

  @Column(name = "expires_at")
  private LocalDate expiresAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private DocumentStatusEnum status = DocumentStatusEnum.PENDING;

  @Enumerated(EnumType.STRING)
  @Column(name = "review_method", length = 30)
  private ReviewMethodEnum reviewMethod;

  @Column(name = "external_check_id", length = 64)
  private String externalCheckId;

  @Column(name = "rejection_reason", length = 255)
  private String rejectionReason;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "reviewed_by")
  private UserModel reviewedBy;

  @Column(name = "reviewed_at")
  private Instant reviewedAt;

  @Column(name = "notified_at")
  private Instant notifiedAt;

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
