package br.com.vanep.dependent.model;

import br.com.vanep.dependent.enums.Shift;
import br.com.vanep.user.Gender;
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
import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.SoftDeleteType;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "dependent")
@SoftDelete(columnName = "deleted_at", strategy = SoftDeleteType.TIMESTAMP)
@Getter
@Setter
public class DependentModel {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 32)
  private String token;

  @Column(name = "client_id", nullable = false)
  private Long clientId;

  @Column(name = "school_id")
  private Long schoolId;

  @Column(name = "address_id")
  private Long addressId;

  @Column(nullable = false)
  private String name;

  @Column(name = "birth_date")
  private LocalDate birthDate;

  @Enumerated(EnumType.STRING)
  @Column(length = 16)
  private Gender gender;

  @Column(length = 64)
  private String document;

  @Column(length = 32)
  private String phone;

  private String email;

  @Column(name = "is_self", nullable = false)
  private boolean self;

  @Column(name = "is_default", nullable = false)
  private boolean defaultDependent;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Shift shift = Shift.MORNING;

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
