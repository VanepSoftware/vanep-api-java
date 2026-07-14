package br.com.vanep.assistant.model;

import br.com.vanep.assistant.enums.AssistantInviteStatus;
import br.com.vanep.driver.model.DriverModel;
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
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.SoftDeleteType;

@Entity
@Table(name = "assistant_invite")
@SoftDelete(columnName = "deleted_at", strategy = SoftDeleteType.TIMESTAMP)
@Getter
@Setter
public class AssistantInviteModel {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** Public opaque ref for API (e.g. cancel invite). Not the email URL secret. */
  @Column(nullable = false, unique = true, length = 32)
  private String token;

  /** SHA-256 hash ({@code SecureTokens.hash}) of the raw token embedded in the email link. */
  @Column(name = "link_token_hash", nullable = false, unique = true, length = 64)
  private String linkTokenHash;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "driver_id", nullable = false)
  private DriverModel driver;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "assistant_id", nullable = false)
  private AssistantModel assistant;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private AssistantInviteStatus status = AssistantInviteStatus.PENDING;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "responded_at")
  private Instant respondedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @PrePersist
  void onCreate() {
    if (token == null) {
      token = UUID.randomUUID().toString().replace("-", "").substring(0, 25);
    }
  }
}
