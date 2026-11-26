package br.com.vanep.notification.repository;

import br.com.vanep.notification.model.NotificationModel;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificationRepository extends JpaRepository<NotificationModel, Long> {

  @Query(
      """
      select notification from NotificationModel notification
      where notification.userId = :userId
      order by notification.createdAt desc, notification.id desc
      """)
  Page<NotificationModel> findByUserIdNewestFirst(Long userId, Pageable pageable);

  long countByUserIdAndReadAtIsNull(Long userId);

  Optional<NotificationModel> findByToken(String token);

  @Query(
      """
      select users.token from NotificationModel notification, UserModel users
      where users.id = notification.userId and notification.token = :token
      """)
  Optional<String> findUserTokenByToken(String token);

  @Modifying
  @Query(
      """
      update NotificationModel notification set notification.readAt = :readAt
      where notification.token = :token and notification.readAt is null
      """)
  int markRead(String token, Instant readAt);

  @Modifying
  @Query(
      """
      update NotificationModel notification set notification.readAt = :readAt
      where notification.userId = :userId and notification.readAt is null
      """)
  int markAllRead(Long userId, Instant readAt);
}
