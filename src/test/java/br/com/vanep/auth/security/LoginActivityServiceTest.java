package br.com.vanep.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoginActivityServiceTest {

  @Mock private LoginAttemptService attempts;
  @Mock private UserRepository users;
  @InjectMocks private LoginActivityService service;

  @Test
  void recordingASuccessResetsTheCounterAndStampsLastLogin() {
    UserModel user = new UserModel();
    when(users.findByEmail("a@vanep.com")).thenReturn(Optional.of(user));

    service.recordSuccessfulLogin("a@vanep.com");

    verify(attempts).loginSucceeded("a@vanep.com");
    assertThat(user.getLastLoginAt()).isNotNull();
  }

  @Test
  void anUnknownEmailStillResetsTheCounter() {
    when(users.findByEmail("nobody@vanep.com")).thenReturn(Optional.empty());

    service.recordSuccessfulLogin("nobody@vanep.com");

    verify(attempts).loginSucceeded("nobody@vanep.com");
  }
}
