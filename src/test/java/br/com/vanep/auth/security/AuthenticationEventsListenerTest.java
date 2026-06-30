package br.com.vanep.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.user.User;
import br.com.vanep.user.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class AuthenticationEventsListenerTest {

  @Mock private LoginAttemptService attempts;
  @Mock private UserRepository users;

  private AuthenticationEventsListener listener;

  @BeforeEach
  void setUp() {
    listener = new AuthenticationEventsListener(attempts, users);
  }

  @Test
  void onBadCredentialsRecordsFailure() {
    Authentication auth = new TestingAuthenticationToken("a@vanep.com", "x");
    listener.onBadCredentials(
        new AuthenticationFailureBadCredentialsEvent(auth, new BadCredentialsException("bad")));
    verify(attempts).loginFailed("a@vanep.com");
  }

  @Test
  void onInteractiveSuccessResetsAndStampsLastLogin() {
    Authentication auth = new TestingAuthenticationToken("a@vanep.com", null);
    User user = new User();
    when(users.findByEmail("a@vanep.com")).thenReturn(Optional.of(user));

    listener.onInteractiveSuccess(new InteractiveAuthenticationSuccessEvent(auth, getClass()));

    verify(attempts).loginSucceeded("a@vanep.com");
    assertThat(user.getLastLoginAt()).isNotNull();
  }
}
