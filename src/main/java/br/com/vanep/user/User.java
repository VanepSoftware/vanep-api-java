package br.com.vanep.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Conta de usuário. Dona de toda a autenticação (local + OAuth via oauth_account). Espelha a tabela
 * `user` do dbdiagram (nomeada `users` por `user` ser palavra reservada no PostgreSQL).
 */
@Entity
@Table(name = "users")
@Getter
@Setter
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 32)
  private String token;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private UserType type;

  @Column(name = "role_id")
  private Long roleId;

  @Column(name = "country_id")
  private Long countryId;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(unique = true)
  private String username;

  /** Nullable: contas só-OAuth não têm senha local. */
  @Column private String password;

  @Column(nullable = false, unique = true, length = 64)
  private String document;

  @Column private String phone;

  @Column(name = "birth_date")
  private LocalDate birthDate;

  @Enumerated(EnumType.STRING)
  @Column(length = 16)
  private Gender gender;

  @Column(nullable = false)
  private boolean verified = false;

  @Column(name = "terms_accepted_at")
  private Instant termsAcceptedAt;

  @Column(name = "last_name_change_at")
  private Instant lastNameChangeAt;

  @Column(name = "last_login_at")
  private Instant lastLoginAt;

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
