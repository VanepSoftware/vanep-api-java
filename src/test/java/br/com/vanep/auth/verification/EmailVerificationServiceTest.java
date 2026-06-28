package br.com.vanep.auth.verification;

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

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

  @Mock private EmailVerificationTokenRepository tokens;
  @Mock private UserRepository users;
  @Mock private MailService mail;

  private EmailVerificationService service;

  @BeforeEach
  void setUp() {
    service = new EmailVerificationService(tokens, users, mail, 24, "http://localhost:8080");
  }

  @Test
  void startVerificationSavesTokenAndSendsEmail() {
    User user = new User();
    user.setId(1L);
    user.setEmail("a@vanep.com");
    user.setName("A");

    service.startVerification(user);

    verify(tokens).save(any(EmailVerificationToken.class));
    verify(mail).send(eq("a@vanep.com"), anyString(), eq("email/verification"), anyMap());
  }

  @Test
  void verifyActivatesUser() {
    String raw = "raw-token";
    EmailVerificationToken token = new EmailVerificationToken();
    token.setUserId(1L);
    token.setExpiresAt(Instant.now().plusSeconds(60));
    when(tokens.findByTokenHash(SecureTokens.hash(raw))).thenReturn(Optional.of(token));
    User user = new User();
    when(users.findById(1L)).thenReturn(Optional.of(user));

    assertThat(service.verify(raw)).isTrue();
    assertThat(user.isVerified()).isTrue();
    assertThat(token.getConsumedAt()).isNotNull();
  }

  @Test
  void verifyFailsForExpiredToken() {
    String raw = "raw-token";
    EmailVerificationToken token = new EmailVerificationToken();
    token.setUserId(1L);
    token.setExpiresAt(Instant.now().minusSeconds(60));
    when(tokens.findByTokenHash(SecureTokens.hash(raw))).thenReturn(Optional.of(token));

    assertThat(service.verify(raw)).isFalse();
    verify(users, never()).findById(any());
  }

  @Test
  void verifyFailsForUnknownToken() {
    when(tokens.findByTokenHash(anyString())).thenReturn(Optional.empty());
    assertThat(service.verify("x")).isFalse();
  }

  @Test
  void verifyFalseForBlank() {
    assertThat(service.verify("  ")).isFalse();
    assertThat(service.verify(null)).isFalse();
  }

  @Test
  void resendSendsForUnverifiedUserOnly() {
    User user = new User();
    user.setId(2L);
    user.setEmail("b@vanep.com");
    user.setName("B");
    user.setVerified(false);
    when(users.findByEmailAndDeletedAtIsNull("b@vanep.com")).thenReturn(Optional.of(user));

    service.resend("b@vanep.com");

    verify(tokens).save(any(EmailVerificationToken.class));
  }

  @Test
  void resendDoesNothingForVerifiedUser() {
    User user = new User();
    user.setVerified(true);
    when(users.findByEmailAndDeletedAtIsNull("c@vanep.com")).thenReturn(Optional.of(user));

    service.resend("c@vanep.com");

    verify(tokens, never()).save(any());
  }
}
