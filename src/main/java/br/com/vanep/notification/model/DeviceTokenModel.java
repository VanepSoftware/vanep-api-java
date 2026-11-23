package br.com.vanep.notification.model;

import br.com.vanep.notification.enums.DevicePlatform;
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
import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.SoftDeleteType;

@Entity
@Table(name = "device_token")
@SoftDelete(columnName = "deleted_at", strategy = SoftDeleteType.TIMESTAMP)
@Getter
@Setter
public class DeviceTokenModel {

  public static final int FCM_TOKEN_MAX_LENGTH = 1024;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // A plain id: the owner is never read through the device, and a to-one on the soft-deleted
  // UserModel would have to be EAGER, adding a user select per device on every batch lookup.
  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "fcm_token", nullable = false, length = FCM_TOKEN_MAX_LENGTH)
  private String fcmToken;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private DevicePlatform platform;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "last_seen_at", nullable = false)
  private Instant lastSeenAt;
}
