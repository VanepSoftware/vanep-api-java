package br.com.vanep.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.SoftDeleteType;

/**
 * {@code created_at} / {@code updated_at}; callbacks cobrem insert mesmo sem trigger no banco.
 * {@code deleted_at} com soft delete Hibernate para entidades que usam essa coluna.
 */
@Getter
@Setter
@MappedSuperclass
@SoftDelete(columnName = "deleted_at", strategy = SoftDeleteType.TIMESTAMP)
public abstract class GenericTimeStampsEntity {

  @Column(name = "created_at", updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  @PrePersist
  protected void onCreate() {
    LocalDateTime now = LocalDateTime.now();
    if (createdAt == null) {
      createdAt = now;
    }
    updatedAt = now;
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = LocalDateTime.now();
  }
}
