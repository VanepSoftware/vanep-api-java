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
import java.util.function.Predicate;
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

  static final Set<MessagingErrorCode> TRANSIENT_ERRORS =
      EnumSet.of(
          MessagingErrorCode.UNAVAILABLE,
          MessagingErrorCode.INTERNAL,
          MessagingErrorCode.QUOTA_EXCEEDED);

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

  /**
   * Returns the device tokens that failed with a transient error, so the caller can retry only
   * those. A null {@code onlyFcmTokens} sends to every device of the user.
   */
  public List<String> sendToUser(
      Long userId,
      String title,
      String body,
      Map<String, String> data,
      List<String> onlyFcmTokens) {
    if (!isEnabled()) {
      return List.of();
    }
    List<String> fcmTokens = findTargetTokens(userId, onlyFcmTokens);
    if (fcmTokens.isEmpty()) {
      return List.of();
    }
    BatchResponse response = sendBatch(userId, buildMessages(fcmTokens, title, body, data));
    logFailures(userId, response.getResponses());
    devices.removeAll(
        findTokensFailedWith(fcmTokens, response.getResponses(), code -> isStaleToken(code)));
    return findTokensFailedWith(fcmTokens, response.getResponses(), code -> isTransient(code));
  }

  static boolean isStaleToken(MessagingErrorCode code) {
    return code != null && STALE_TOKEN_ERRORS.contains(code);
  }

  // No FCM error code means the call never got an FCM answer (network down, token endpoint
  // unreachable): the SDK reports that per device instead of failing the batch.
  static boolean isTransient(MessagingErrorCode code) {
    return code == null || TRANSIENT_ERRORS.contains(code);
  }

  // A retry targets the devices that failed before, minus any removed meanwhile.
  List<String> findTargetTokens(Long userId, List<String> onlyFcmTokens) {
    List<String> registered = devices.findFcmTokensOf(userId);
    if (onlyFcmTokens == null) {
      return registered;
    }
    return registered.stream().filter(token -> onlyFcmTokens.contains(token)).toList();
  }

  BatchResponse sendBatch(Long userId, List<Message> messages) {
    try {
      return messaging.sendEach(messages);
    } catch (FirebaseMessagingException batchFailure) {
      throw new PushDeliveryException(userId, batchFailure);
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

  List<String> findTokensFailedWith(
      List<String> fcmTokens,
      List<SendResponse> responses,
      Predicate<MessagingErrorCode> matchesError) {
    List<String> failed = new ArrayList<>();
    for (int index = 0; index < responses.size(); index++) {
      SendResponse response = responses.get(index);
      if (!response.isSuccessful()
          && matchesError.test(response.getException().getMessagingErrorCode())) {
        failed.add(fcmTokens.get(index));
      }
    }
    return failed;
  }

  public static class PushDeliveryException extends RuntimeException {

    PushDeliveryException(Long userId, FirebaseMessagingException cause) {
      super("FCM rejected the whole push batch for user " + userId, cause);
    }
  }
}
