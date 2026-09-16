package br.com.vanep.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class VanepUserDetailsServiceTest {

  private static final String EMAIL = "person@vanep.com";

  @Mock private UserRepository users;
  @Mock private LoginAttemptService loginAttempts;
  @InjectMocks private VanepUserDetailsService service;

  private UserModel account() {
    UserModel user = new UserModel();
    user.setType(UserType.CLIENT);
    user.setEmail(EMAIL);
    user.setPassword("stored-hash");
    user.setVerified(true);
    return user;
  }

  @Test
  void blockedEmailIsLockedBeforeTheAccountIsEvenLookedUp() {
    when(loginAttempts.isBlocked(EMAIL)).thenReturn(true);

    assertThatThrownBy(() -> service.loadUserByUsername(EMAIL)).isInstanceOf(LockedException.class);

    verify(users, never()).findByEmail(EMAIL);
  }

  @Test
  void blockedUnknownEmailIsLockedTheSameWay() {
    when(loginAttempts.isBlocked("nobody@vanep.com")).thenReturn(true);

    assertThatThrownBy(() -> service.loadUserByUsername("nobody@vanep.com"))
        .isInstanceOf(LockedException.class);

    verify(users, never()).findByEmail("nobody@vanep.com");
  }

  @Test
  void blockedGoogleOnlyEmailIsLockedTheSameWay() {
    when(loginAttempts.isBlocked(EMAIL)).thenReturn(true);

    assertThatThrownBy(() -> service.loadUserByUsername(EMAIL)).isInstanceOf(LockedException.class);
  }

  @Test
  void unknownEmailIsNotFoundWhenNotBlocked() {
    when(loginAttempts.isBlocked("nobody@vanep.com")).thenReturn(false);
    when(users.findByEmail("nobody@vanep.com")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.loadUserByUsername("nobody@vanep.com"))
        .isInstanceOf(UsernameNotFoundException.class);
  }

  @Test
  void googleOnlyAccountIsNotFoundWhenNotBlocked() {
    UserModel googleOnly = account();
    googleOnly.setPassword(null);
    when(loginAttempts.isBlocked(EMAIL)).thenReturn(false);
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(googleOnly));

    assertThatThrownBy(() -> service.loadUserByUsername(EMAIL))
        .isInstanceOf(UsernameNotFoundException.class);
  }

  @Test
  void unverifiedAccountLoadsAsDisabled() {
    UserModel unverified = account();
    unverified.setVerified(false);
    when(loginAttempts.isBlocked(EMAIL)).thenReturn(false);
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(unverified));

    assertThat(service.loadUserByUsername(EMAIL).isEnabled()).isFalse();
  }
}
