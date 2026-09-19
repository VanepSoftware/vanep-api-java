package br.com.vanep.auth.verification;

import br.com.vanep.auth.enums.AuthCodePurpose;
import br.com.vanep.auth.mail.MailService;
import br.com.vanep.auth.token.AuthCodeIssueDecision;
import br.com.vanep.auth.token.AuthCodeIssuePolicy;
import br.com.vanep.auth.token.SecureCodes;
import br.com.vanep.auth.token.SecureTokens;
import br.com.vanep.auth.verification.model.EmailVerificationTokenModel;
import br.com.vanep.user.exception.ProfileEmailDuplicateException;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailVerificationService {

  private final EmailVerificationTokenRepository tokens;
  private final UserRepository users;
  private final MailService mail;
  private final MessageSource messages;
  private final SecureCodes codes;
  private final AuthCodeIssuePolicy issuePolicy;
  private final int maxAttempts;
  private final Duration ttl;
  private final String baseUrl;

  public EmailVerificationService(
      EmailVerificationTokenRepository tokens,
      UserRepository users,
      MailService mail,
      MessageSource messages,
      SecureCodes codes,
      AuthCodeIssuePolicy issuePolicy,
      @Value("${vanep.auth.code.max-attempts:5}") int maxAttempts,
      @Value("${vanep.mail.verification-ttl-hours:24}") long ttlHours,
      @Value("${vanep.app.base-url:http://localhost:8080}") String baseUrl) {
    this.tokens = tokens;
    this.users = users;
    this.mail = mail;
    this.messages = messages;
    this.codes = codes;
    this.issuePolicy = issuePolicy;
    this.maxAttempts = maxAttempts;
    this.ttl = Duration.ofHours(ttlHours);
    this.baseUrl = baseUrl;
  }

  private String message(String key, Object... args) {
    return messages.getMessage(key, args, LocaleContextHolder.getLocale());
  }

  @Transactional
  public void startVerification(UserModel user) {
    Instant now = Instant.now();
    // The e-mail change confirmation carries no code, so the code limits do not apply to it:
    // throttling it would silently drop the only link the profile screen relies on.
    if (hasPendingEmail(user)) {
      String rawToken = issueToken(user, now, null);
      mail.send(
          user.getPendingEmail(),
          message("user.profile.email.change.subject"),
          "email/email-change",
          Map.of("name", user.getName(), "link", verificationLink(rawToken)));
      return;
    }
    if (issuePolicy.decide(lastIssuedAt(user), issuedInWindow(user, now), now)
        == AuthCodeIssueDecision.SKIP) {
      return;
    }
    String code = codes.generate();
    String rawToken = issueToken(user, now, code);
    mail.send(
        user.getEmail(),
        message("auth.email.verification.subject"),
        "email/verification",
        Map.of(
            "name",
            user.getName(),
            "link",
            verificationLink(rawToken),
            "code",
            code,
            "ttl",
            message("auth.email.validity.hours", ttl.toHours())));
  }

  /**
   * The e-mail change send bypasses the throttling inside {@link #startVerification}, so the caller
   * has to ask here first and reject the request explicitly instead of dropping the link silently.
   *
   * @return empty when a new send is allowed; otherwise when it becomes allowed
   */
  @Transactional(readOnly = true)
  public Optional<Instant> issueRetryAfter(UserModel user) {
    Instant now = Instant.now();
    return issuePolicy.retryAfter(lastIssuedAt(user), issuedInWindow(user, now), now);
  }

  private String issueToken(UserModel user, Instant now, String code) {
    tokens.consumeAllActive(user.getId(), now);
    String raw = SecureTokens.generate();
    EmailVerificationTokenModel token = new EmailVerificationTokenModel();
    token.setUserId(user.getId());
    token.setTokenHash(SecureTokens.hash(raw));
    if (code != null) {
      token.setCodeHash(codes.hmac(AuthCodePurpose.EMAIL_VERIFICATION, user.getId(), code));
    }
    token.setExpiresAt(now.plus(ttl));
    tokens.save(token);
    return raw;
  }

  private String verificationLink(String rawToken) {
    return baseUrl + "/verify-email?token=" + rawToken;
  }

  @Transactional
  public boolean verify(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      return false;
    }
    Optional<EmailVerificationTokenModel> maybe =
        tokens.findByTokenHash(SecureTokens.hash(rawToken));
    if (maybe.isEmpty()) {
      return false;
    }
    EmailVerificationTokenModel token = maybe.get();
    if (token.getConsumedAt() != null || token.getExpiresAt().isBefore(Instant.now())) {
      return false;
    }
    Optional<UserModel> maybeUser = users.findById(token.getUserId());
    if (maybeUser.isEmpty()) {
      return false;
    }
    UserModel user = maybeUser.get();
    Instant now = Instant.now();
    String pending = user.getPendingEmail();
    if (pending != null && !pending.isBlank()) {
      promotePendingEmail(user, pending, now);
    } else {
      user.setVerified(true);
    }
    token.setConsumedAt(now);
    return true;
  }

  /** The code and the link are one credential: using either consumes the other. */
  @Transactional
  public boolean verifyByCode(String email, String code) {
    Optional<UserModel> maybeUser =
        users
            .findByEmail(email)
            .filter(user -> !user.isVerified())
            .filter(user -> !hasPendingEmail(user));
    if (maybeUser.isEmpty() || code == null) {
      return false;
    }
    UserModel user = maybeUser.get();
    Instant now = Instant.now();
    Optional<EmailVerificationTokenModel> maybeToken = tokens.lockLatestActive(user.getId(), now);
    if (maybeToken.isEmpty()) {
      return false;
    }
    EmailVerificationTokenModel token = maybeToken.get();
    if (!codes.matches(
        token.getCodeHash(), AuthCodePurpose.EMAIL_VERIFICATION, user.getId(), code)) {
      registerFailedAttempt(token, now);
      return false;
    }
    user.setVerified(true);
    token.setConsumedAt(now);
    return true;
  }

  private void registerFailedAttempt(EmailVerificationTokenModel token, Instant now) {
    token.setFailedAttempts(token.getFailedAttempts() + 1);
    if (token.getFailedAttempts() >= maxAttempts) {
      token.setConsumedAt(now);
    }
  }

  void promotePendingEmail(UserModel user, String pending, Instant now) {
    if (users.existsByEmail(pending)) {
      throw new ProfileEmailDuplicateException(message("auth.signup.email.duplicate"));
    }
    try {
      user.setEmail(pending);
      user.setPendingEmail(null);
      user.setLastEmailChangeAt(now);
      user.setVerified(true);
      users.saveAndFlush(user);
    } catch (DataIntegrityViolationException ex) {
      throw new ProfileEmailDuplicateException(message("auth.signup.email.duplicate"));
    }
  }

  @Transactional
  public void resend(String email) {
    users.findByEmail(email).filter(user -> !user.isVerified()).ifPresent(this::startVerification);
  }

  private static boolean hasPendingEmail(UserModel user) {
    return user.getPendingEmail() != null && !user.getPendingEmail().isBlank();
  }

  private long issuedInWindow(UserModel user, Instant now) {
    return tokens.countByUserIdAndCreatedAtAfter(user.getId(), issuePolicy.dailyWindowStart(now));
  }

  private Instant lastIssuedAt(UserModel user) {
    return tokens
        .findFirstByUserIdOrderByCreatedAtDesc(user.getId())
        .map(EmailVerificationTokenModel::getCreatedAt)
        .orElse(null);
  }
}
