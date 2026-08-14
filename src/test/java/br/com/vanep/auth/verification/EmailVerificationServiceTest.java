package br.com.vanep.auth.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.auth.mail.MailService;
import br.com.vanep.auth.token.SecureTokens;
import br.com.vanep.auth.verification.model.EmailVerificationTokenModel;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.enums.ProfileErrorCode;
import br.com.vanep.user.exception.ProfileEmailDuplicateException;
import br.com.vanep.user.model.UserModel;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

  @Mock private EmailVerificationTokenRepository tokens;
  @Mock private UserRepository users;
  @Mock private MailService mail;
  @Mock private MessageSource messages;

  private EmailVerificationService service;

  @BeforeEach
  void setUp() {
    service =
        new EmailVerificationService(tokens, users, mail, messages, 24, "http://localhost:8080");
  }

  @Test
  void startVerificationConsumesOpenTokensSavesNewAndSendsEmail() {
    UserModel user = new UserModel();
    user.setId(1L);
    user.setEmail("a@vanep.com");
    user.setName("A");
    when(messages.getMessage(eq("auth.email.verification.subject"), any(), any()))
        .thenReturn("Confirme seu e-mail — Vanep");

    service.startVerification(user);

    verify(tokens).consumeAllActive(eq(1L), any(Instant.class));
    verify(tokens).save(any(EmailVerificationTokenModel.class));
    verify(mail)
        .send(
            eq("a@vanep.com"),
            eq("Confirme seu e-mail — Vanep"),
            eq("email/verification"),
            anyMap());
  }

  @Test
  void startVerificationSendsToPendingEmailWhenSet() {
    UserModel user = new UserModel();
    user.setId(1L);
    user.setEmail("old@vanep.com");
    user.setPendingEmail("new@vanep.com");
    user.setName("A");
    when(messages.getMessage(eq("user.profile.email.change.subject"), any(), any()))
        .thenReturn("Confirme a alteração do seu e-mail — Vanep");

    service.startVerification(user);

    verify(tokens).consumeAllActive(eq(1L), any(Instant.class));
    verify(mail)
        .send(
            eq("new@vanep.com"),
            eq("Confirme a alteração do seu e-mail — Vanep"),
            eq("email/email-change"),
            anyMap());
  }

  @Test
  void verifyActivatesUserWithoutPending() {
    String raw = "raw-token";
    EmailVerificationTokenModel token = new EmailVerificationTokenModel();
    token.setUserId(1L);
    token.setExpiresAt(Instant.now().plusSeconds(60));
    when(tokens.findByTokenHash(SecureTokens.hash(raw))).thenReturn(Optional.of(token));
    UserModel user = new UserModel();
    user.setEmail("a@vanep.com");
    when(users.findById(1L)).thenReturn(Optional.of(user));

    assertThat(service.verify(raw)).isTrue();
    assertThat(user.isVerified()).isTrue();
    assertThat(user.getEmail()).isEqualTo("a@vanep.com");
    assertThat(user.getPendingEmail()).isNull();
    assertThat(user.getLastEmailChangeAt()).isNull();
    assertThat(token.getConsumedAt()).isNotNull();
  }

  @Test
  void verifyWithPendingPromotesEmailAndSetsCooldown() {
    String raw = "raw-token";
    EmailVerificationTokenModel token = new EmailVerificationTokenModel();
    token.setUserId(1L);
    token.setExpiresAt(Instant.now().plusSeconds(60));
    when(tokens.findByTokenHash(SecureTokens.hash(raw))).thenReturn(Optional.of(token));
    UserModel user = new UserModel();
    user.setEmail("old@vanep.com");
    user.setPendingEmail("new@vanep.com");
    when(users.findById(1L)).thenReturn(Optional.of(user));
    when(users.existsByEmail("new@vanep.com")).thenReturn(false);
    when(users.saveAndFlush(user)).thenReturn(user);

    assertThat(service.verify(raw)).isTrue();
    assertThat(user.getEmail()).isEqualTo("new@vanep.com");
    assertThat(user.getPendingEmail()).isNull();
    assertThat(user.isVerified()).isTrue();
    assertThat(user.getLastEmailChangeAt()).isNotNull();
    assertThat(token.getConsumedAt()).isNotNull();
  }

  @Test
  void verifyWithPendingDuplicateThrowsAndDoesNotPromote() {
    String raw = "raw-token";
    EmailVerificationTokenModel token = new EmailVerificationTokenModel();
    token.setUserId(1L);
    token.setExpiresAt(Instant.now().plusSeconds(60));
    when(tokens.findByTokenHash(SecureTokens.hash(raw))).thenReturn(Optional.of(token));
    UserModel user = new UserModel();
    user.setEmail("old@vanep.com");
    user.setPendingEmail("taken@vanep.com");
    when(users.findById(1L)).thenReturn(Optional.of(user));
    when(users.existsByEmail("taken@vanep.com")).thenReturn(true);
    when(messages.getMessage(anyString(), any(), any())).thenAnswer(inv -> inv.getArgument(0));

    assertThatThrownBy(() -> service.verify(raw))
        .isInstanceOf(ProfileEmailDuplicateException.class)
        .satisfies(
            ex -> {
              ProfileEmailDuplicateException pe = (ProfileEmailDuplicateException) ex;
              assertThat(pe.getCode()).isEqualTo(ProfileErrorCode.EMAIL_DUPLICATE);
              assertThat(pe.getField()).isEqualTo("email");
              assertThat(pe.getRetryAfter()).isNull();
              assertThat(pe.getMessage()).isEqualTo("auth.signup.email.duplicate");
            });
    assertThat(user.getEmail()).isEqualTo("old@vanep.com");
    assertThat(user.getPendingEmail()).isEqualTo("taken@vanep.com");
    assertThat(token.getConsumedAt()).isNull();
  }

  @Test
  void verifyWithPendingRaceOnUniqueThrowsDuplicate() {
    String raw = "raw-token";
    EmailVerificationTokenModel token = new EmailVerificationTokenModel();
    token.setUserId(1L);
    token.setExpiresAt(Instant.now().plusSeconds(60));
    when(tokens.findByTokenHash(SecureTokens.hash(raw))).thenReturn(Optional.of(token));
    UserModel user = new UserModel();
    user.setEmail("old@vanep.com");
    user.setPendingEmail("race@vanep.com");
    when(users.findById(1L)).thenReturn(Optional.of(user));
    when(users.existsByEmail("race@vanep.com")).thenReturn(false);
    when(users.saveAndFlush(user)).thenThrow(new DataIntegrityViolationException("unique"));
    when(messages.getMessage(anyString(), any(), any())).thenAnswer(inv -> inv.getArgument(0));

    assertThatThrownBy(() -> service.verify(raw))
        .isInstanceOf(ProfileEmailDuplicateException.class);
  }

  @Test
  void verifyFailsForConsumedToken() {
    String raw = "raw-token";
    EmailVerificationTokenModel token = new EmailVerificationTokenModel();
    token.setUserId(1L);
    token.setExpiresAt(Instant.now().plusSeconds(60));
    token.setConsumedAt(Instant.now());
    when(tokens.findByTokenHash(SecureTokens.hash(raw))).thenReturn(Optional.of(token));

    assertThat(service.verify(raw)).isFalse();
    verify(users, never()).findById(anyLong());
  }

  @Test
  void verifyFailsForExpiredToken() {
    String raw = "raw-token";
    EmailVerificationTokenModel token = new EmailVerificationTokenModel();
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
    UserModel user = new UserModel();
    user.setId(2L);
    user.setEmail("b@vanep.com");
    user.setName("B");
    user.setVerified(false);
    when(users.findByEmail("b@vanep.com")).thenReturn(Optional.of(user));
    when(messages.getMessage(eq("auth.email.verification.subject"), any(), any()))
        .thenReturn("Confirme seu e-mail — Vanep");

    service.resend("b@vanep.com");

    verify(tokens).consumeAllActive(eq(2L), any(Instant.class));
    verify(tokens).save(any(EmailVerificationTokenModel.class));
  }

  @Test
  void resendDoesNothingForVerifiedUser() {
    UserModel user = new UserModel();
    user.setVerified(true);
    when(users.findByEmail("c@vanep.com")).thenReturn(Optional.of(user));

    service.resend("c@vanep.com");

    verify(tokens, never()).save(any());
    verify(tokens, never()).consumeAllActive(anyLong(), any());
  }

  @Test
  void startVerificationIssuesNewTokenAfterConsumingPrior() {
    UserModel user = new UserModel();
    user.setId(5L);
    user.setEmail("user@vanep.com");
    user.setPendingEmail("b@vanep.com");
    user.setName("U");
    when(messages.getMessage(eq("user.profile.email.change.subject"), any(), any()))
        .thenReturn("Confirme a alteração do seu e-mail — Vanep");

    ArgumentCaptor<EmailVerificationTokenModel> captor =
        ArgumentCaptor.forClass(EmailVerificationTokenModel.class);

    service.startVerification(user);

    verify(tokens).consumeAllActive(eq(5L), any(Instant.class));
    verify(tokens).save(captor.capture());
    assertThat(captor.getValue().getUserId()).isEqualTo(5L);
    assertThat(captor.getValue().getTokenHash()).isNotBlank();
    verify(mail).send(eq("b@vanep.com"), anyString(), eq("email/email-change"), anyMap());
  }
}
