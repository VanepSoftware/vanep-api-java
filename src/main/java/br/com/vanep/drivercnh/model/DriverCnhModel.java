package br.com.vanep.drivercnh.model;

import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.media.model.MediaFileModel;
import br.com.vanep.shared.document.enums.DocumentKind;
import br.com.vanep.shared.document.model.DocumentReview;
import br.com.vanep.shared.document.model.DocumentSubmission;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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
@Table(name = "driver_cnh")
@SoftDelete(columnName = "deleted_at", strategy = SoftDeleteType.TIMESTAMP)
@Getter
@Setter
public class DriverCnhModel implements DocumentSubmission {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 32)
  private String token;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "driver_id", nullable = false)
  private DriverModel driver;

  @Column(name = "registration_number", length = 20)
  private String registrationNumber;

  @Column(length = 5)
  private String category;

  @Column(name = "issue_date")
  private LocalDate issueDate;

  @Column(name = "valid_until")
  private LocalDate validUntil;

  @Column(name = "first_license_date")
  private LocalDate firstLicenseDate;

  @Column(name = "security_number", length = 20)
  private String securityNumber;

  @Column(name = "issuing_state", length = 2)
  private String issuingState;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "file_media_id")
  private MediaFileModel file;

  @Embedded private DocumentReview review = DocumentReview.pending();

  @Column(name = "notified_at")
  private Instant notifiedAt;

  @Version
  @Column(nullable = false)
  private Long version;

  @Column(name = "superseded_at")
  private Instant supersededAt;

  @Column(name = "is_active", nullable = false)
  private boolean active = true;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Override
  public DocumentKind kind() {
    return DocumentKind.DRIVER_CNH;
  }

  @Override
  public String number() {
    return registrationNumber;
  }

  @Override
  public LocalDate expiresOn() {
    return validUntil;
  }

  @Override
  public MediaFileModel getReviewedFile() {
    return file;
  }

  @Override
  public void setReviewedFile(MediaFileModel file) {
    this.file = file;
  }

  @PrePersist
  void onCreate() {
    if (token == null) {
      token = UUID.randomUUID().toString().replace("-", "").substring(0, 25);
    }
  }
}
