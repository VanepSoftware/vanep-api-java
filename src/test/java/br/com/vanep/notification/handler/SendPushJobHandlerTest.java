package br.com.vanep.notification.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.vanep.job.enums.JobType;
import br.com.vanep.job.handler.JobRetryException;
import br.com.vanep.job.handler.PermanentJobFailureException;
import br.com.vanep.notification.enums.NotificationType;
import br.com.vanep.notification.model.NotificationModel;
import br.com.vanep.notification.service.NotificationService;
import br.com.vanep.notification.service.PushNotificationService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SendPushJobHandlerTest {

  private static final Map<String, String> PUSH_DATA =
      Map.of("type", "PROPOSAL_RECEIVED", "notificationToken", "n-1");

  @Mock private NotificationService notifications;
  @Mock private PushNotificationService push;

  private SendPushJobHandler handler;
  private NotificationModel notification;

  @BeforeEach
  void setUp() {
    handler = new SendPushJobHandler(notifications, push);
    notification = new NotificationModel();
    notification.setToken("n-1");
    notification.setUserId(7L);
    notification.setType(NotificationType.PROPOSAL_RECEIVED);
    notification.setTitle("Nova proposta");
    notification.setBody("Toque para ver.");
  }

  @Test
  void handlesThePushJobType() {
    assertThat(handler.type()).isEqualTo(JobType.SEND_PUSH);
    assertThat(handler.payloadType()).isEqualTo(SendPushJobHandler.Payload.class);
  }

  @Test
  void sendsTheStoredNotificationToEveryDevice() {
    givenStored();
    when(push.sendToUser(7L, "Nova proposta", "Toque para ver.", PUSH_DATA, null))
        .thenReturn(List.of());

    handler.perform(new SendPushJobHandler.Payload("n-1", null));

    verify(push).sendToUser(7L, "Nova proposta", "Toque para ver.", PUSH_DATA, null);
  }

  @Test
  void aRetrySendsOnlyToTheDevicesThatFailedBefore() {
    givenStored();
    when(push.sendToUser(7L, "Nova proposta", "Toque para ver.", PUSH_DATA, List.of("fcm-2")))
        .thenReturn(List.of());

    handler.perform(new SendPushJobHandler.Payload("n-1", List.of("fcm-2")));

    verify(push).sendToUser(7L, "Nova proposta", "Toque para ver.", PUSH_DATA, List.of("fcm-2"));
  }

  @Test
  void transientDeviceFailuresAskForARetryOfThoseDevicesOnly() {
    givenStored();
    when(push.sendToUser(7L, "Nova proposta", "Toque para ver.", PUSH_DATA, null))
        .thenReturn(List.of("fcm-2"));

    assertThatThrownBy(() -> handler.perform(new SendPushJobHandler.Payload("n-1", null)))
        .isInstanceOfSatisfying(
            JobRetryException.class,
            retry ->
                assertThat(retry.replacementPayload())
                    .contains(new SendPushJobHandler.Payload("n-1", List.of("fcm-2"))));
  }

  @Test
  void aVanishedNotificationFailsPermanently() {
    when(notifications.findByToken("n-1")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> handler.perform(new SendPushJobHandler.Payload("n-1", null)))
        .isInstanceOf(PermanentJobFailureException.class);
    verifyNoInteractions(push);
  }

  private void givenStored() {
    when(notifications.findByToken("n-1")).thenReturn(Optional.of(notification));
    when(notifications.pushData(notification)).thenReturn(PUSH_DATA);
  }
}
