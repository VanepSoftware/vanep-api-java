package br.com.vanep.auth.token.model;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@MappedSuperclass
@Getter
@Setter
public abstract class OneTimeTokenModel {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "token_hash", nullable = false, unique = true, length = 64)
  private String tokenHash;

  // Not unique: the code space is 10^6, so two accounts can legitimately collide.
  @Column(name = "code_hash", length = 64)
  private String codeHash;

  @Column(name = "failed_attempts", nullable = false)
  private int failedAttempts;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "consumed_at")
  private Instant consumedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
