package br.com.vanep.driver.model;

import br.com.vanep.driver.DriverLinkCodeStatus;
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
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "driver_link_code")
@Getter
@Setter
public class DriverLinkCodeModel {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "driver_id", nullable = false)
  private DriverModel driver;

  @Column(name = "code_hash", nullable = false, unique = true, length = 64)
  private String codeHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private DriverLinkCodeStatus status = DriverLinkCodeStatus.ACTIVE;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "consumed_at")
  private Instant consumedAt;

  @Column(name = "consumed_by_assistant_id")
  private Long consumedByAssistantId;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
