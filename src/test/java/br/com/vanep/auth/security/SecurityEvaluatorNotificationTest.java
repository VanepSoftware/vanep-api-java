package br.com.vanep.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import br.com.vanep.notification.repository.NotificationRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class SecurityEvaluatorNotificationTest {

  @Mock private NotificationRepository notificationRepository;

  @InjectMocks private SecurityEvaluator evaluator;

  @Test
  void theRecipientOwnsTheNotification() {
    when(notificationRepository.findUserTokenByToken("n-1")).thenReturn(Optional.of("maria-uid"));

    assertThat(evaluator.isNotificationOwner("n-1", callerWithUid("maria-uid"))).isTrue();
  }

  @Test
  void anotherUserDoesNotOwnTheNotification() {
    when(notificationRepository.findUserTokenByToken("n-1")).thenReturn(Optional.of("maria-uid"));

    assertThat(evaluator.isNotificationOwner("n-1", callerWithUid("carlos-uid"))).isFalse();
  }

  @Test
  void anUnknownNotificationHasNoOwner() {
    when(notificationRepository.findUserTokenByToken("missing")).thenReturn(Optional.empty());

    assertThat(evaluator.isNotificationOwner("missing", callerWithUid("maria-uid"))).isFalse();
  }

  @Test
  void aCallerWithoutUidOwnsNothing() {
    Authentication anonymous = new TestingAuthenticationToken("someone", null);

    assertThat(evaluator.isNotificationOwner("n-1", anonymous)).isFalse();
  }

  private Authentication callerWithUid(String uid) {
    Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").claim("uid", uid).build();
    return new JwtAuthenticationToken(jwt);
  }
}
