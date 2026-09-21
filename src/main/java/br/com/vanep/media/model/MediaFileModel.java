package br.com.vanep.media.model;

import br.com.vanep.media.enums.MediaVisibility;
import br.com.vanep.media.enums.StorageProvider;
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
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.SoftDeleteType;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "media_file")
@SoftDelete(columnName = "deleted_at", strategy = SoftDeleteType.TIMESTAMP)
@Getter
@Setter
public class MediaFileModel {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 32)
  private String token;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private StorageProvider provider;

  @Column(name = "object_key", nullable = false, length = 512)
  private String objectKey;

  @Column(name = "mime_type", nullable = false, length = 100)
  private String mimeType;

  @Column(name = "size_bytes", nullable = false)
  private Long sizeBytes;

  @Column(name = "original_name", length = 255)
  private String originalName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private MediaVisibility visibility;

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
    if (provider == null) {
      provider = StorageProvider.LOCAL;
    }
    if (visibility == null) {
      visibility = MediaVisibility.PRIVATE;
    }
  }
}
