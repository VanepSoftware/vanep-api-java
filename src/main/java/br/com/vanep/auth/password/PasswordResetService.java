package br.com.vanep.auth.password;

import br.com.vanep.auth.enums.AuthCodePurpose;
import br.com.vanep.auth.mail.MailService;
import br.com.vanep.auth.password.model.PasswordResetTokenModel;
import br.com.vanep.auth.token.AuthCodeIssueDecision;
import br.com.vanep.auth.token.AuthCodeIssuePolicy;
import br.com.vanep.auth.token.SecureCodes;
import br.com.vanep.auth.token.SecureTokens;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {

  // An unknown e-mail still runs the token lookup. The 400 body is already uniform, but returning
  // early skips a query, and the difference in response time tells registered accounts apart.
  private static final Long NO_SUCH_USER_ID = -1L;

  private final PasswordResetTokenRepository tokens;
  private final UserRepository users;
  private final MailService mail;
  private final MessageSource messages;
  private final PasswordEncoder passwordEncoder;
  private final SecureCodes codes;
  private final AuthCodeIssuePolicy issuePolicy;
  private final int maxAttempts;
  private final Duration ttl;
  private final String baseUrl;

  public PasswordResetService(
      PasswordResetTokenRepository tokens,
      UserRepository users,
      MailService mail,
      MessageSource messages,
      PasswordEncoder passwordEncoder,
      SecureCodes codes,
      AuthCodeIssuePolicy issuePolicy,
      @Value("${vanep.auth.code.max-attempts:5}") int maxAttempts,
      @Value("${vanep.mail.reset-ttl-minutes:15}") long ttlMinutes,
      @Value("${vanep.app.base-url:http://localhost:8080}") String baseUrl) {
    this.tokens = tokens;
    this.users = users;
    this.mail = mail;
    this.messages = messages;
    this.passwordEncoder = passwordEncoder;
    this.codes = codes;
    this.issuePolicy = issuePolicy;
    this.maxAttempts = maxAttempts;
    this.ttl = Duration.ofMinutes(ttlMinutes);
    this.baseUrl = baseUrl;
  }

  private String message(String key, Object... args) {
    return messages.getMessage(key, args, LocaleContextHolder.getLocale());
  }

  @Transactional
  public void requestReset(String email) {
    users.findByEmail(email).filter(PasswordResetService::hasLocalPassword).ifPresent(this::issue);
  }

  private void issue(UserModel user) {
    Instant now = Instant.now();
    if (issuePolicy.decide(lastIssuedAt(user), issuedInWindow(user, now), now)
        == AuthCodeIssueDecision.SKIP) {
      return;
    }
    tokens.consumeAllActive(user.getId(), now);

    String raw = SecureTokens.generate();
    String code = codes.generate();
    PasswordResetTokenModel token = new PasswordResetTokenModel();
    token.setUserId(user.getId());
    token.setTokenHash(SecureTokens.hash(raw));
    token.setCodeHash(codes.hmac(AuthCodePurpose.PASSWORD_RESET, user.getId(), code));
    token.setExpiresAt(now.plus(ttl));
    tokens.save(token);

    mail.send(
        user.getEmail(),
        message("auth.password.reset.subject"),
        "email/password-reset",
        Map.of(
            "name",
            user.getName(),
            "link",
            baseUrl + "/reset-password?token=" + raw,
            "code",
            code,
            "ttl",
            message("auth.email.validity.minutes", ttl.toMinutes())));
  }

  public boolean isValidToken(String rawToken) {
    return findValid(rawToken).isPresent();
  }

  @Transactional
  public boolean reset(String rawToken, String newPassword) {
    Optional<PasswordResetTokenModel> maybe = findValid(rawToken);
    if (maybe.isEmpty()) {
      return false;
    }
    PasswordResetTokenModel token = maybe.get();
    Optional<UserModel> user = users.findById(token.getUserId());
    if (user.isEmpty()) {
      return false;
    }
    user.get().setPassword(passwordEncoder.encode(newPassword));
    token.setConsumedAt(Instant.now());
    return true;
  }

  /** The code and the link are one credential: using either consumes the other. */
  @Transactional
  public boolean resetByCode(String email, String code, String newPassword) {
    Optional<UserModel> maybeUser =
        users.findByEmail(email).filter(PasswordResetService::hasLocalPassword);
    Instant now = Instant.now();
    Optional<PasswordResetTokenModel> maybeToken =
        tokens.lockLatestActive(maybeUser.map(user -> user.getId()).orElse(NO_SUCH_USER_ID), now);
    if (maybeUser.isEmpty() || code == null || maybeToken.isEmpty()) {
      return false;
    }
    UserModel user = maybeUser.get();
    PasswordResetTokenModel token = maybeToken.get();
    if (!codes.matches(token.getCodeHash(), AuthCodePurpose.PASSWORD_RESET, user.getId(), code)) {
      registerFailedAttempt(token, now);
      return false;
    }
    user.setPassword(passwordEncoder.encode(newPassword));
    token.setConsumedAt(now);
    return true;
  }

  private void registerFailedAttempt(PasswordResetTokenModel token, Instant now) {
    token.setFailedAttempts(token.getFailedAttempts() + 1);
    if (token.getFailedAttempts() >= maxAttempts) {
      token.setConsumedAt(now);
    }
  }

  private Optional<PasswordResetTokenModel> findValid(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      return Optional.empty();
    }
    return tokens
        .findByTokenHash(SecureTokens.hash(rawToken))
        .filter(
            token -> token.getConsumedAt() == null && token.getExpiresAt().isAfter(Instant.now()));
  }

  private static boolean hasLocalPassword(UserModel user) {
    return user.getPassword() != null && !user.getPassword().isBlank();
  }

  private long issuedInWindow(UserModel user, Instant now) {
    return tokens.countByUserIdAndCreatedAtAfter(user.getId(), issuePolicy.dailyWindowStart(now));
  }

  private Instant lastIssuedAt(UserModel user) {
    return tokens
        .findFirstByUserIdOrderByCreatedAtDesc(user.getId())
        .map(token -> token.getCreatedAt())
        .orElse(null);
  }
}
