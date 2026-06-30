package br.com.vanep.auth.password;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.auth.mail.MailService;
import br.com.vanep.auth.token.SecureTokens;
import br.com.vanep.user.User;
import br.com.vanep.user.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

  @Mock private PasswordResetTokenRepository tokens;
  @Mock private UserRepository users;
  @Mock private MailService mail;
  @Mock private PasswordEncoder passwordEncoder;

  private PasswordResetService service;

  @BeforeEach
  void setUp() {
    service =
        new PasswordResetService(tokens, users, mail, passwordEncoder, 60, "http://localhost");
  }

  @Test
  void requestResetEmitsTokenForLocalUser() {
    User user = new User();
    user.setId(1L);
    user.setEmail("a@vanep.com");
    user.setName("A");
    user.setPassword("hashed");
    when(users.findByEmail("a@vanep.com")).thenReturn(Optional.of(user));

    service.requestReset("a@vanep.com");

    verify(tokens).consumeAllActive(org.mockito.ArgumentMatchers.eq(1L), any(Instant.class));
    verify(tokens).save(any(PasswordResetToken.class));
    verify(mail).send(eq("a@vanep.com"), anyString(), eq("email/password-reset"), anyMap());
  }

  @Test
  void requestResetIgnoresUnknownEmail() {
    when(users.findByEmail("x@vanep.com")).thenReturn(Optional.empty());
    service.requestReset("x@vanep.com");
    verify(tokens, never()).save(any());
  }

  @Test
  void requestResetIgnoresOauthOnlyAccount() {
    User user = new User();
    user.setEmail("o@vanep.com");
    user.setPassword(null);
    when(users.findByEmail("o@vanep.com")).thenReturn(Optional.of(user));

    service.requestReset("o@vanep.com");

    verify(tokens, never()).save(any());
  }

  @Test
  void resetChangesPasswordForValidToken() {
    String raw = "raw";
    PasswordResetToken token = new PasswordResetToken();
    token.setUserId(1L);
    token.setExpiresAt(Instant.now().plusSeconds(60));
    when(tokens.findByTokenHash(SecureTokens.hash(raw))).thenReturn(Optional.of(token));
    User user = new User();
    when(users.findById(1L)).thenReturn(Optional.of(user));
    when(passwordEncoder.encode("newpass12")).thenReturn("new-hash");

    assertThat(service.reset(raw, "newpass12")).isTrue();
    assertThat(user.getPassword()).isEqualTo("new-hash");
    assertThat(token.getConsumedAt()).isNotNull();
  }

  @Test
  void resetFailsForInvalidToken() {
    when(tokens.findByTokenHash(anyString())).thenReturn(Optional.empty());
    assertThat(service.reset("x", "newpass12")).isFalse();
  }

  @Test
  void isValidTokenReflectsExpiry() {
    String raw = "raw";
    PasswordResetToken token = new PasswordResetToken();
    token.setExpiresAt(Instant.now().plusSeconds(60));
    when(tokens.findByTokenHash(SecureTokens.hash(raw))).thenReturn(Optional.of(token));

    assertThat(service.isValidToken(raw)).isTrue();
    assertThat(service.isValidToken(null)).isFalse();
  }
}
