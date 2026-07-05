package br.com.vanep.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import br.com.vanep.user.User;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.UserType;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class VanepUserDetailsServiceTest {

  @Mock private UserRepository users;
  @Mock private LoginAttemptService loginAttempts;

  private VanepUserDetailsService service;

  @BeforeEach
  void setUp() {
    service = new VanepUserDetailsService(users, loginAttempts);
  }

  private User verifiedUser() {
    User user = new User();
    user.setEmail("a@vanep.com");
    user.setPassword("hashed");
    user.setType(UserType.CLIENT);
    user.setVerified(true);
    return user;
  }

  @Test
  void loadsEnabledUnlockedUser() {
    when(users.findByEmail("a@vanep.com")).thenReturn(Optional.of(verifiedUser()));
    when(loginAttempts.isBlocked("a@vanep.com")).thenReturn(false);

    UserDetails details = service.loadUserByUsername("a@vanep.com");

    assertThat(details.isEnabled()).isTrue();
    assertThat(details.isAccountNonLocked()).isTrue();
    assertThat(details.getAuthorities())
        .extracting(authority -> authority.toString())
        .containsExactly("ROLE_CLIENT");
  }

  @Test
  void unverifiedUserIsDisabled() {
    User user = verifiedUser();
    user.setVerified(false);
    when(users.findByEmail("a@vanep.com")).thenReturn(Optional.of(user));

    assertThat(service.loadUserByUsername("a@vanep.com").isEnabled()).isFalse();
  }

  @Test
  void blockedUserIsLocked() {
    when(users.findByEmail("a@vanep.com")).thenReturn(Optional.of(verifiedUser()));
    when(loginAttempts.isBlocked("a@vanep.com")).thenReturn(true);

    assertThat(service.loadUserByUsername("a@vanep.com").isAccountNonLocked()).isFalse();
  }

  @Test
  void unknownUserThrows() {
    when(users.findByEmail("ghost@vanep.com")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.loadUserByUsername("ghost@vanep.com"))
        .isInstanceOf(UsernameNotFoundException.class);
  }

  @Test
  void accountWithoutLocalPasswordThrows() {
    User user = verifiedUser();
    user.setPassword(null);
    when(users.findByEmail("a@vanep.com")).thenReturn(Optional.of(user));
    assertThatThrownBy(() -> service.loadUserByUsername("a@vanep.com"))
        .isInstanceOf(UsernameNotFoundException.class);
  }
}
