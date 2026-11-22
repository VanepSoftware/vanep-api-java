package br.com.vanep.vehicledocument.model;

import br.com.vanep.media.model.MediaFileModel;
import br.com.vanep.shared.document.enums.DocumentKind;
import br.com.vanep.shared.document.model.DocumentReview;
import br.com.vanep.shared.document.model.DocumentSubmission;
import br.com.vanep.vehicle.model.VehicleModel;
import br.com.vanep.vehicledocument.enums.VehicleDocumentType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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
@Table(name = "vehicle_document")
@SoftDelete(columnName = "deleted_at", strategy = SoftDeleteType.TIMESTAMP)
@Getter
@Setter
public class VehicleDocumentModel implements DocumentSubmission {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 32)
  private String token;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "vehicle_id", nullable = false)
  private VehicleModel vehicle;

  @Enumerated(EnumType.STRING)
  @Column(name = "document_type", nullable = false, length = 50)
  private VehicleDocumentType documentType;

  @Column(name = "document_number", length = 64)
  private String documentNumber;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "file_media_id")
  private MediaFileModel file;

  @Column(name = "expires_at")
  private LocalDate expiresAt;

  @Embedded private DocumentReview review = DocumentReview.pending();

  @Column(name = "notified_at")
  private Instant notifiedAt;

  @Version
  @Column(nullable = false)
  private Long version;

  @Column(name = "superseded_at")
  private Instant supersededAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Override
  public DocumentKind kind() {
    return DocumentKind.VEHICLE_DOCUMENT;
  }

  @Override
  public String number() {
    return documentNumber;
  }

  @Override
  public LocalDate expiresOn() {
    return expiresAt;
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
