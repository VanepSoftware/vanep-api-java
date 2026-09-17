package br.com.vanep.auth.security;

import static org.mockito.Mockito.verify;

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
  @Mock private LoginActivityService loginActivity;

  private AuthenticationEventsListener listener;

  @BeforeEach
  void setUp() {
    listener = new AuthenticationEventsListener(attempts, loginActivity);
  }

  @Test
  void onBadCredentialsRecordsFailure() {
    Authentication auth = new TestingAuthenticationToken("a@vanep.com", "x");
    listener.onBadCredentials(
        new AuthenticationFailureBadCredentialsEvent(auth, new BadCredentialsException("bad")));
    verify(attempts).loginFailed("a@vanep.com");
  }

  @Test
  void onInteractiveSuccessDelegatesToTheSharedService() {
    Authentication auth = new TestingAuthenticationToken("a@vanep.com", null);

    listener.onInteractiveSuccess(new InteractiveAuthenticationSuccessEvent(auth, getClass()));

    verify(loginActivity).recordSuccessfulLogin("a@vanep.com");
  }
}
