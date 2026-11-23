package br.com.vanep.notification.service;

import br.com.vanep.notification.dto.NotificationResponseDTO;
import br.com.vanep.notification.enums.NotificationType;
import br.com.vanep.notification.model.NotificationModel;
import br.com.vanep.notification.repository.NotificationRepository;
import br.com.vanep.user.service.UserService;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

  public static final String TYPE_KEY = "type";
  public static final String NOTIFICATION_TOKEN_KEY = "notificationToken";

  private final NotificationRepository notifications;
  private final PushNotificationService push;
  private final UserService users;
  private final Clock clock;

  public NotificationService(
      NotificationRepository notifications,
      PushNotificationService push,
      UserService users,
      Clock clock) {
    this.notifications = notifications;
    this.push = push;
    this.users = users;
    this.clock = clock;
  }

  // Stored before the push, so the push can carry the token the app marks as read on tap, and
  // the notification is listed even when push is disabled or FCM is down.
  public void notifyUser(
      Long userId, NotificationType type, String title, String body, Map<String, String> data) {
    NotificationModel saved = notifications.save(newNotification(userId, type, title, body, data));
    push.sendToUser(userId, title, body, pushData(saved));
  }

  NotificationModel newNotification(
      Long userId, NotificationType type, String title, String body, Map<String, String> data) {
    NotificationModel notification = new NotificationModel();
    notification.setUserId(userId);
    notification.setType(type);
    notification.setTitle(title);
    notification.setBody(body);
    notification.setData(Map.copyOf(data));
    return notification;
  }

  Map<String, String> pushData(NotificationModel notification) {
    Map<String, String> data = new HashMap<>(notification.getData());
    data.put(TYPE_KEY, notification.getType().name());
    data.put(NOTIFICATION_TOKEN_KEY, notification.getToken());
    return data;
  }

  @Transactional(readOnly = true)
  public Page<NotificationResponseDTO> listMine(String callerUid, Pageable pageable) {
    Long userId = users.requireByToken(callerUid).getId();
    Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    return notifications.findByUserIdNewestFirst(userId, unsorted).map(this::toResponse);
  }

  NotificationResponseDTO toResponse(NotificationModel notification) {
    return new NotificationResponseDTO(
        notification.getToken(),
        notification.getType(),
        notification.getTitle(),
        notification.getBody(),
        notification.getData(),
        notification.getCreatedAt(),
        notification.getReadAt());
  }

  @Transactional(readOnly = true)
  public long countMyUnread(String callerUid) {
    return notifications.countByUserIdAndReadAtIsNull(users.requireByToken(callerUid).getId());
  }

  @Transactional
  public void markRead(String token) {
    notifications.markRead(token, Instant.now(clock));
  }

  @Transactional
  public void markAllMineRead(String callerUid) {
    notifications.markAllRead(users.requireByToken(callerUid).getId(), Instant.now(clock));
  }
}
