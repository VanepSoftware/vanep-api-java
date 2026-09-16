package br.com.vanep.auth.signup.model;

import br.com.vanep.user.enums.AuthProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/** Consumable credential, like the tokens in V5: no soft delete. */
@Entity
@Table(name = "signup_ticket")
@Getter
@Setter
public class SignupTicketModel {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "ticket_hash", nullable = false, unique = true, length = 64)
  private String ticketHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "provider", nullable = false, length = 16)
  private AuthProvider provider;

  @Column(name = "provider_uid", nullable = false)
  private String providerUid;

  @Column(name = "email", nullable = false)
  private String email;

  @Column(name = "name")
  private String name;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "consumed_at")
  private Instant consumedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
