package br.com.vanep.auth.password;

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
import br.com.vanep.auth.password.model.PasswordResetTokenModel;
import br.com.vanep.auth.token.AuthCodeIssuePolicy;
import br.com.vanep.auth.token.SecureCodes;
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
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

  private static final long USER_ID = 11L;
  private static final String EMAIL = "person@vanep.com";

  @Mock private PasswordResetTokenRepository tokens;
  @Mock private UserRepository users;
  @Mock private MailService mail;
  @Mock private MessageSource messages;
  @Mock private PasswordEncoder passwordEncoder;

  private final SecureCodes codes = new SecureCodes("pepper-for-tests");
  private PasswordResetService service;

  @BeforeEach
  void setUp() {
    service =
        new PasswordResetService(
            tokens,
            users,
            mail,
            messages,
            passwordEncoder,
            codes,
            new AuthCodeIssuePolicy(60, 10),
            5,
            15,
            "http://localhost:8080");
  }

  private UserModel userWithLocalPassword() {
    UserModel user = new UserModel();
    user.setId(USER_ID);
    user.setName("Person");
    user.setEmail(EMAIL);
    user.setPassword("stored-hash");
    return user;
  }

  private void stubMessages() {
    when(messages.getMessage(anyString(), any(), any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> capturedMailModel() {
    ArgumentCaptor<Map<String, Object>> model = ArgumentCaptor.forClass(Map.class);
    verify(mail).send(anyString(), anyString(), anyString(), model.capture());
    return model.getValue();
  }

  @Test
  void requestResetStoresTheCodeHashAndSendsCodeLinkAndValidity() {
    stubMessages();
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(userWithLocalPassword()));
    when(tokens.save(any(PasswordResetTokenModel.class))).thenAnswer(inv -> inv.getArgument(0));

    service.requestReset(EMAIL);

    ArgumentCaptor<PasswordResetTokenModel> saved =
        ArgumentCaptor.forClass(PasswordResetTokenModel.class);
    verify(tokens).save(saved.capture());
    Map<String, Object> model = capturedMailModel();
    String code = model.get("code").toString();
    assertThat(code).hasSize(6).containsOnlyDigits();
    assertThat(saved.getValue().getCodeHash())
        .isEqualTo(codes.hmac(AuthCodePurpose.PASSWORD_RESET, USER_ID, code));
    assertThat(model.get("link").toString()).contains("/reset-password?token=");
    assertThat(model).containsKey("ttl");
  }

  @Test
  void anAccountWithoutLocalPasswordGetsNothing() {
    UserModel googleOnly = userWithLocalPassword();
    googleOnly.setPassword(null);
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(googleOnly));

    service.requestReset(EMAIL);

    verify(mail, never()).send(anyString(), anyString(), anyString(), anyMap());
    verify(tokens, never()).save(any());
  }

  @Test
  void requestInsideTheCooldownSendsNothing() {
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(userWithLocalPassword()));
    PasswordResetTokenModel previous = new PasswordResetTokenModel();
    previous.setCreatedAt(Instant.now().minus(Duration.ofSeconds(30)));
    when(tokens.findFirstByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.of(previous));

    service.requestReset(EMAIL);

    verify(mail, never()).send(anyString(), anyString(), anyString(), anyMap());
  }

  @Test
  void requestBeyondTheDailyCapSendsNothing() {
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(userWithLocalPassword()));
    when(tokens.countByUserIdAndCreatedAtAfter(eq(USER_ID), any(Instant.class))).thenReturn(10L);

    service.requestReset(EMAIL);

    verify(mail, never()).send(anyString(), anyString(), anyString(), anyMap());
  }

  @Test
  void resetByCodeStoresTheNewPasswordAndConsumesTheCodeAndLink() {
    UserModel user = userWithLocalPassword();
    PasswordResetTokenModel token = activeTokenFor("654321");
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    when(tokens.lockLatestActive(eq(USER_ID), any(Instant.class))).thenReturn(Optional.of(token));
    when(passwordEncoder.encode("new-password")).thenReturn("new-hash");

    assertThat(service.resetByCode(EMAIL, "654321", "new-password")).isTrue();

    assertThat(user.getPassword()).isEqualTo("new-hash");
    assertThat(token.getConsumedAt()).isNotNull();
  }

  @Test
  void wrongCodeCountsAnAttemptAndKeepsThePassword() {
    UserModel user = userWithLocalPassword();
    PasswordResetTokenModel token = activeTokenFor("654321");
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    when(tokens.lockLatestActive(eq(USER_ID), any(Instant.class))).thenReturn(Optional.of(token));

    assertThat(service.resetByCode(EMAIL, "000000", "new-password")).isFalse();

    assertThat(user.getPassword()).isEqualTo("stored-hash");
    assertThat(token.getFailedAttempts()).isEqualTo(1);
    verify(passwordEncoder, never()).encode(anyString());
  }

  @Test
  void theFifthWrongCodeBurnsTheCodeAndItsLink() {
    UserModel user = userWithLocalPassword();
    PasswordResetTokenModel token = activeTokenFor("654321");
    token.setFailedAttempts(4);
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    when(tokens.lockLatestActive(eq(USER_ID), any(Instant.class))).thenReturn(Optional.of(token));

    assertThat(service.resetByCode(EMAIL, "000000", "new-password")).isFalse();

    assertThat(token.getConsumedAt()).isNotNull();
  }

  @Test
  void resetByCodeRefusesUnknownEmailsAndAccountsWithoutLocalPassword() {
    when(users.findByEmail("nobody@vanep.com")).thenReturn(Optional.empty());
    assertThat(service.resetByCode("nobody@vanep.com", "654321", "new-password")).isFalse();

    UserModel googleOnly = userWithLocalPassword();
    googleOnly.setPassword(null);
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(googleOnly));
    assertThat(service.resetByCode(EMAIL, "654321", "new-password")).isFalse();

    verify(tokens, never()).lockLatestActive(any(), any());
  }

  private PasswordResetTokenModel activeTokenFor(String code) {
    PasswordResetTokenModel token = new PasswordResetTokenModel();
    token.setUserId(USER_ID);
    token.setCodeHash(codes.hmac(AuthCodePurpose.PASSWORD_RESET, USER_ID, code));
    token.setExpiresAt(Instant.now().plus(Duration.ofMinutes(15)));
    return token;
  }
}
