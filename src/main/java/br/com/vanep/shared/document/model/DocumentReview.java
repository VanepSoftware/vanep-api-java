package br.com.vanep.shared.document.model;

import br.com.vanep.shared.document.enums.DocumentRejectionReason;
import br.com.vanep.shared.document.enums.DocumentStatus;
import br.com.vanep.shared.document.enums.ReviewMethod;
import br.com.vanep.user.model.UserModel;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

@Embeddable
@Getter
@Setter
public class DocumentReview {

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private DocumentStatus status = DocumentStatus.PENDING;

  @Enumerated(EnumType.STRING)
  @Column(name = "review_method", length = 30)
  private ReviewMethod reviewMethod;

  @Column(name = "external_check_id", length = 64)
  private String externalCheckId;

  @Enumerated(EnumType.STRING)
  @Column(name = "rejection_reason_code", length = 30)
  private @Nullable DocumentRejectionReason rejectionReasonCode;

  @Column(name = "rejection_reason", length = 255)
  private @Nullable String rejectionReason;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "reviewed_by")
  private @Nullable UserModel reviewedBy;

  @Column(name = "reviewed_at")
  private Instant reviewedAt;

  @Column(name = "data_corrected_by")
  private Long dataCorrectedById;

  @Column(name = "data_corrected_at")
  private Instant dataCorrectedAt;

  public static DocumentReview pending() {
    return new DocumentReview();
  }
}
