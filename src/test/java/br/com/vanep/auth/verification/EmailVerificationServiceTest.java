package br.com.vanep.auth.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.auth.enums.AuthCodePurpose;
import br.com.vanep.auth.mail.MailService;
import br.com.vanep.auth.token.AuthCodeIssuePolicy;
import br.com.vanep.auth.token.SecureCodes;
import br.com.vanep.auth.verification.model.EmailVerificationTokenModel;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

  private static final long USER_ID = 7L;
  private static final String EMAIL = "person@vanep.com";

  @Mock private EmailVerificationTokenRepository tokens;
  @Mock private UserRepository users;
  @Mock private MailService mail;
  @Mock private MessageSource messages;

  private final SecureCodes codes = new SecureCodes("pepper-for-tests");
  private EmailVerificationService service;

  @BeforeEach
  void setUp() {
    service =
        new EmailVerificationService(
            tokens,
            users,
            mail,
            messages,
            codes,
            new AuthCodeIssuePolicy(60, 10),
            5,
            24,
            "http://localhost:8080");
  }

  private UserModel unverifiedUser() {
    UserModel user = new UserModel();
    user.setId(USER_ID);
    user.setName("Person");
    user.setEmail(EMAIL);
    user.setVerified(false);
    return user;
  }

  private void stubMessages() {
    when(messages.getMessage(anyString(), any(), any()))
        .thenAnswer(
            invocation -> {
              String key = invocation.getArgument(0);
              if (key == null) {
                throw new NoSuchMessageException("null");
              }
              return key;
            });
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> capturedMailModel() {
    ArgumentCaptor<Map<String, Object>> model = ArgumentCaptor.forClass(Map.class);
    verify(mail).send(anyString(), anyString(), anyString(), model.capture());
    return model.getValue();
  }

  @Test
  void startVerificationStoresTheCodeHashAndSendsCodeLinkAndValidity() {
    stubMessages();
    UserModel user = unverifiedUser();
    when(tokens.save(any(EmailVerificationTokenModel.class))).thenAnswer(inv -> inv.getArgument(0));

    service.startVerification(user);

    ArgumentCaptor<EmailVerificationTokenModel> saved =
        ArgumentCaptor.forClass(EmailVerificationTokenModel.class);
    verify(tokens).save(saved.capture());
    Map<String, Object> model = capturedMailModel();
    String code = model.get("code").toString();
    assertThat(code).hasSize(6).containsOnlyDigits();
    assertThat(saved.getValue().getCodeHash())
        .isEqualTo(codes.hmac(AuthCodePurpose.EMAIL_VERIFICATION, USER_ID, code))
        .isNotEqualTo(code);
    assertThat(model.get("link").toString()).contains("/verify-email?token=");
    assertThat(model).containsKey("ttl");
    verify(tokens).consumeAllActive(eq(USER_ID), any(Instant.class));
  }

  @Test
  void emailChangeKeepsTheLinkOnlyTemplate() {
    stubMessages();
    UserModel user = unverifiedUser();
    user.setPendingEmail("new@vanep.com");
    when(tokens.save(any(EmailVerificationTokenModel.class))).thenAnswer(inv -> inv.getArgument(0));

    service.startVerification(user);

    verify(mail).send(eq("new@vanep.com"), anyString(), eq("email/email-change"), anyMap());
    assertThat(capturedMailModel()).doesNotContainKey("code");
  }

  @Test
  void resendInsideTheCooldownSendsNothingAndDoesNotFail() {
    UserModel user = unverifiedUser();
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    EmailVerificationTokenModel previous = new EmailVerificationTokenModel();
    previous.setCreatedAt(Instant.now().minus(Duration.ofSeconds(5)));
    when(tokens.findFirstByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.of(previous));

    service.resend(EMAIL);

    verify(mail, never()).send(anyString(), anyString(), anyString(), anyMap());
    verify(tokens, never()).save(any());
  }

  @Test
  void resendBeyondTheDailyCapSendsNothing() {
    UserModel user = unverifiedUser();
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    when(tokens.countByUserIdAndCreatedAtAfter(eq(USER_ID), any(Instant.class))).thenReturn(10L);

    service.resend(EMAIL);

    verify(mail, never()).send(anyString(), anyString(), anyString(), anyMap());
  }

  @Test
  void verifyByCodeMarksTheAccountVerifiedAndConsumesTheCode() {
    UserModel user = unverifiedUser();
    EmailVerificationTokenModel token = activeTokenFor("123456");
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    when(tokens.lockLatestActive(eq(USER_ID), any(Instant.class))).thenReturn(Optional.of(token));

    assertThat(service.verifyByCode(EMAIL, "123456")).isTrue();

    assertThat(user.isVerified()).isTrue();
    assertThat(token.getConsumedAt()).isNotNull();
  }

  @Test
  void wrongCodeCountsAnAttemptWithoutVerifying() {
    UserModel user = unverifiedUser();
    EmailVerificationTokenModel token = activeTokenFor("123456");
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    when(tokens.lockLatestActive(eq(USER_ID), any(Instant.class))).thenReturn(Optional.of(token));

    assertThat(service.verifyByCode(EMAIL, "000000")).isFalse();

    assertThat(user.isVerified()).isFalse();
    assertThat(token.getFailedAttempts()).isEqualTo(1);
    assertThat(token.getConsumedAt()).isNull();
  }

  @Test
  void theFifthWrongCodeBurnsTheCodeAndItsLink() {
    UserModel user = unverifiedUser();
    EmailVerificationTokenModel token = activeTokenFor("123456");
    token.setFailedAttempts(4);
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    when(tokens.lockLatestActive(eq(USER_ID), any(Instant.class))).thenReturn(Optional.of(token));

    assertThat(service.verifyByCode(EMAIL, "000000")).isFalse();

    assertThat(token.getFailedAttempts()).isEqualTo(5);
    assertThat(token.getConsumedAt()).isNotNull();
  }

  @Test
  void verifyByCodeRefusesVerifiedAccountsPendingChangesAndUnknownEmails() {
    UserModel verified = unverifiedUser();
    verified.setVerified(true);
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(verified));
    assertThat(service.verifyByCode(EMAIL, "123456")).isFalse();

    UserModel pending = unverifiedUser();
    pending.setPendingEmail("new@vanep.com");
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(pending));
    assertThat(service.verifyByCode(EMAIL, "123456")).isFalse();

    when(users.findByEmail("nobody@vanep.com")).thenReturn(Optional.empty());
    assertThat(service.verifyByCode("nobody@vanep.com", "123456")).isFalse();

    verify(tokens, never()).lockLatestActive(any(), any());
  }

  private EmailVerificationTokenModel activeTokenFor(String code) {
    EmailVerificationTokenModel token = new EmailVerificationTokenModel();
    token.setUserId(USER_ID);
    token.setCodeHash(codes.hmac(AuthCodePurpose.EMAIL_VERIFICATION, USER_ID, code));
    token.setExpiresAt(Instant.now().plus(Duration.ofHours(24)));
    return token;
  }
}
