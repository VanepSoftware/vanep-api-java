package br.com.vanep.notification.service;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class PushNotificationService {

  private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);

  // FCM answers INVALID_ARGUMENT for a malformed token as well as for a malformed payload; the
  // payload is built here and never malformed, so the token is the one to blame.
  static final Set<MessagingErrorCode> STALE_TOKEN_ERRORS =
      EnumSet.of(MessagingErrorCode.UNREGISTERED, MessagingErrorCode.INVALID_ARGUMENT);

  private final FirebaseMessaging messaging;
  private final DeviceTokenService devices;

  public PushNotificationService(
      ObjectProvider<FirebaseMessaging> messaging, DeviceTokenService devices) {
    this.messaging = messaging.getIfAvailable();
    this.devices = devices;
    if (this.messaging == null) {
      log.warn("Push notifications are disabled: FIREBASE_CREDENTIALS_PATH is not set.");
    }
  }

  public boolean isEnabled() {
    return messaging != null;
  }

  public void sendToUser(Long userId, String title, String body, Map<String, String> data) {
    if (!isEnabled()) {
      return;
    }
    List<String> fcmTokens = devices.findFcmTokensOf(userId);
    if (fcmTokens.isEmpty()) {
      return;
    }
    try {
      BatchResponse response = messaging.sendEach(buildMessages(fcmTokens, title, body, data));
      logFailures(userId, response.getResponses());
      devices.removeAll(findStaleTokens(fcmTokens, response.getResponses()));
    } catch (FirebaseMessagingException ex) {
      log.error("Failed to send push notification to user {}.", userId, ex);
    }
  }

  List<Message> buildMessages(
      List<String> fcmTokens, String title, String body, Map<String, String> data) {
    Notification notification = Notification.builder().setTitle(title).setBody(body).build();
    AndroidConfig android =
        AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build();
    return fcmTokens.stream()
        .map(
            fcmToken ->
                Message.builder()
                    .setToken(fcmToken)
                    .setNotification(notification)
                    .setAndroidConfig(android)
                    .putAllData(data)
                    .build())
        .toList();
  }

  // sendEach reports a per-device failure in the batch instead of throwing, so broken credentials
  // or an FCM outage would otherwise fail every push silently.
  void logFailures(Long userId, List<SendResponse> responses) {
    List<FirebaseMessagingException> failures =
        responses.stream()
            .filter(response -> !response.isSuccessful())
            .map(response -> response.getException())
            .toList();
    if (!failures.isEmpty()) {
      log.warn(
          "Push notification to user {} failed on {} of {} devices.",
          userId,
          failures.size(),
          responses.size(),
          failures.getFirst());
    }
  }

  List<String> findStaleTokens(List<String> fcmTokens, List<SendResponse> responses) {
    List<String> stale = new ArrayList<>();
    for (int index = 0; index < responses.size(); index++) {
      SendResponse response = responses.get(index);
      if (!response.isSuccessful()
          && STALE_TOKEN_ERRORS.contains(response.getException().getMessagingErrorCode())) {
        stale.add(fcmTokens.get(index));
      }
    }
    return stale;
  }
}
