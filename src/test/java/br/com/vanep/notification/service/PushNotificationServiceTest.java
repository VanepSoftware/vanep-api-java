package br.com.vanep.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.api.client.json.gson.GsonFactory;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.SendResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

@ExtendWith(MockitoExtension.class)
class PushNotificationServiceTest {

  private static final Map<String, String> DATA =
      Map.of("type", "CHAT_MESSAGE", "conversationToken", "conv-1");

  @Mock private FirebaseMessaging messaging;
  @Mock private DeviceTokenService devices;
  @Captor private ArgumentCaptor<List<Message>> sent;

  private PushNotificationService service;

  @BeforeEach
  void setUp() {
    service = new PushNotificationService(providerOf(messaging), devices);
  }

  @Test
  void sendsOneMessageToEachDeviceOfTheUser() throws Exception {
    when(devices.findFcmTokensOf(7L)).thenReturn(List.of("fcm-1", "fcm-2"));
    BatchResponse delivered = batch(success(), success());
    when(messaging.sendEach(any())).thenReturn(delivered);

    service.sendToUser(7L, "Nova mensagem de Maria", "Toque para abrir a conversa.", DATA, null);

    verify(messaging).sendEach(sent.capture());
    assertThat(sent.getValue()).hasSize(2);
    String first = toJson(sent.getValue().getFirst());
    assertThat(first)
        .contains("\"token\":\"fcm-1\"")
        .contains("\"title\":\"Nova mensagem de Maria\"")
        .contains("\"body\":\"Toque para abrir a conversa.\"")
        .contains("\"type\":\"CHAT_MESSAGE\"")
        .contains("\"conversationToken\":\"conv-1\"")
        .contains("\"priority\":\"high\"");
    assertThat(toJson(sent.getValue().getLast())).contains("\"token\":\"fcm-2\"");
    verify(devices).removeAll(List.of());
  }

  @Test
  void removesStaleTokensAndReturnsOnlyTheTransientFailuresForRetry() throws Exception {
    when(devices.findFcmTokensOf(7L))
        .thenReturn(List.of("ok", "gone", "malformed", "busy", "broken", "mismatch"));
    BatchResponse partial =
        batch(
            success(),
            failure(MessagingErrorCode.UNREGISTERED),
            failure(MessagingErrorCode.INVALID_ARGUMENT),
            failure(MessagingErrorCode.UNAVAILABLE),
            failure(MessagingErrorCode.INTERNAL),
            failure(MessagingErrorCode.SENDER_ID_MISMATCH));
    when(messaging.sendEach(any())).thenReturn(partial);

    List<String> retry = service.sendToUser(7L, "title", "body", DATA, null);

    verify(devices).removeAll(List.of("gone", "malformed"));
    assertThat(retry).containsExactly("busy", "broken");
  }

  @Test
  void aDeviceFailureWithoutAnFcmErrorCodeIsRetried() throws Exception {
    when(devices.findFcmTokensOf(7L)).thenReturn(List.of("offline"));
    BatchResponse networkDown = batch(failure(null));
    when(messaging.sendEach(any())).thenReturn(networkDown);

    List<String> retry = service.sendToUser(7L, "title", "body", DATA, null);

    assertThat(retry).containsExactly("offline");
    verify(devices).removeAll(List.of());
  }

  @Test
  void aRetrySendsOnlyToTheListedDevicesThatAreStillRegistered() throws Exception {
    when(devices.findFcmTokensOf(7L)).thenReturn(List.of("fcm-1", "fcm-2", "fcm-3"));
    BatchResponse delivered = batch(success());
    when(messaging.sendEach(any())).thenReturn(delivered);

    List<String> retry =
        service.sendToUser(7L, "title", "body", DATA, List.of("fcm-2", "removed-meanwhile"));

    verify(messaging).sendEach(sent.capture());
    assertThat(sent.getValue()).hasSize(1);
    assertThat(toJson(sent.getValue().getFirst())).contains("\"token\":\"fcm-2\"");
    assertThat(retry).isEmpty();
  }

  @Test
  void aUserWithoutDevicesGetsNothingSent() {
    when(devices.findFcmTokensOf(7L)).thenReturn(List.of());

    List<String> retry = service.sendToUser(7L, "title", "body", DATA, null);

    verifyNoInteractions(messaging);
    assertThat(retry).isEmpty();
  }

  @Test
  void aFailedBatchThrowsSoTheJobIsRetriedAndRemovesNoTokens() throws Exception {
    when(devices.findFcmTokensOf(7L)).thenReturn(List.of("fcm-1"));
    when(messaging.sendEach(any())).thenThrow(mock(FirebaseMessagingException.class));

    assertThatThrownBy(() -> service.sendToUser(7L, "title", "body", DATA, null))
        .isInstanceOf(PushNotificationService.PushDeliveryException.class);
    verify(devices, never()).removeAll(any());
  }

  @Test
  void withoutCredentialsPushIsDisabledAndNothingIsLookedUp() {
    PushNotificationService disabled = new PushNotificationService(providerOf(null), devices);

    disabled.sendToUser(7L, "title", "body", DATA, null);

    assertThat(disabled.isEnabled()).isFalse();
    verifyNoInteractions(devices);
  }

  private static ObjectProvider<FirebaseMessaging> providerOf(FirebaseMessaging messaging) {
    StaticListableBeanFactory beans = new StaticListableBeanFactory();
    if (messaging != null) {
      beans.addBean("firebaseMessaging", messaging);
    }
    return beans.getBeanProvider(FirebaseMessaging.class);
  }

  private static BatchResponse batch(SendResponse... responses) {
    BatchResponse batch = mock(BatchResponse.class);
    when(batch.getResponses()).thenReturn(List.of(responses));
    return batch;
  }

  private static SendResponse success() {
    SendResponse response = mock(SendResponse.class);
    when(response.isSuccessful()).thenReturn(true);
    return response;
  }

  private static SendResponse failure(MessagingErrorCode code) {
    FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
    when(exception.getMessagingErrorCode()).thenReturn(code);
    SendResponse response = mock(SendResponse.class);
    when(response.getException()).thenReturn(exception);
    return response;
  }

  private static String toJson(Message message) throws IOException {
    return GsonFactory.getDefaultInstance().toString(message);
  }
}
