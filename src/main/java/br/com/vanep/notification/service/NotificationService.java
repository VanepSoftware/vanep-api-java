package br.com.vanep.notification.service;

import br.com.vanep.job.enums.JobType;
import br.com.vanep.job.service.JobService;
import br.com.vanep.notification.dto.NotificationResponseDTO;
import br.com.vanep.notification.enums.NotificationType;
import br.com.vanep.notification.handler.SendPushJobHandler;
import br.com.vanep.notification.model.NotificationModel;
import br.com.vanep.notification.repository.NotificationRepository;
import br.com.vanep.user.service.UserService;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

  public static final String TYPE_KEY = "type";
  public static final String NOTIFICATION_TOKEN_KEY = "notificationToken";

  private final NotificationRepository notifications;
  private final JobService jobs;
  private final UserService users;
  private final Clock clock;

  public NotificationService(
      NotificationRepository notifications, JobService jobs, UserService users, Clock clock) {
    this.notifications = notifications;
    this.jobs = jobs;
    this.users = users;
    this.clock = clock;
  }

  // Stored in the caller's transaction and pushed by a job, so a retried push never stores the
  // notification twice and the in-app list never depends on FCM.
  @Transactional(propagation = Propagation.MANDATORY)
  public NotificationModel notifyUser(
      Long userId, NotificationType type, String title, String body, Map<String, String> data) {
    NotificationModel saved = notifications.save(newNotification(userId, type, title, body, data));
    jobs.enqueue(JobType.SEND_PUSH, new SendPushJobHandler.Payload(saved.getToken(), null));
    return saved;
  }

  @Transactional(readOnly = true)
  public Optional<NotificationModel> findByToken(String token) {
    return notifications.findByToken(token);
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

  public Map<String, String> pushData(NotificationModel notification) {
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
