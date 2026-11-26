package br.com.vanep.notification.handler;

import br.com.vanep.job.enums.JobType;
import br.com.vanep.job.handler.JobHandler;
import br.com.vanep.job.handler.JobRetryException;
import br.com.vanep.job.handler.PermanentJobFailureException;
import br.com.vanep.notification.model.NotificationModel;
import br.com.vanep.notification.service.NotificationService;
import br.com.vanep.notification.service.PushNotificationService;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SendPushJobHandler implements JobHandler<SendPushJobHandler.Payload> {

  public record Payload(String notificationToken, List<String> fcmTokens) {}

  private final NotificationService notifications;
  private final PushNotificationService push;

  public SendPushJobHandler(NotificationService notifications, PushNotificationService push) {
    this.notifications = notifications;
    this.push = push;
  }

  @Override
  public JobType type() {
    return JobType.SEND_PUSH;
  }

  @Override
  public Class<Payload> payloadType() {
    return Payload.class;
  }

  @Override
  public void perform(Payload payload) {
    NotificationModel notification =
        notifications
            .findByToken(payload.notificationToken())
            .orElseThrow(
                () ->
                    new PermanentJobFailureException(
                        "Notification " + payload.notificationToken() + " no longer exists"));
    List<String> failedTokens =
        push.sendToUser(
            notification.getUserId(),
            notification.getTitle(),
            notification.getBody(),
            notifications.pushData(notification),
            payload.fcmTokens());
    if (!failedTokens.isEmpty()) {
      throw new JobRetryException(
          "Push failed transiently on %d devices".formatted(failedTokens.size()),
          new Payload(payload.notificationToken(), failedTokens));
    }
  }
}
