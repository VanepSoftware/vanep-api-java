package br.com.vanep.auth.verification;

import br.com.vanep.auth.mail.MailService;
import br.com.vanep.auth.token.SecureTokens;
import br.com.vanep.auth.verification.model.EmailVerificationTokenModel;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.exception.ProfileEmailDuplicateException;
import br.com.vanep.user.model.UserModel;
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
  private final Duration ttl;
  private final String baseUrl;

  public EmailVerificationService(
      EmailVerificationTokenRepository tokens,
      UserRepository users,
      MailService mail,
      MessageSource messages,
      @Value("${vanep.mail.verification-ttl-hours:24}") long ttlHours,
      @Value("${vanep.app.base-url:http://localhost:8080}") String baseUrl) {
    this.tokens = tokens;
    this.users = users;
    this.mail = mail;
    this.messages = messages;
    this.ttl = Duration.ofHours(ttlHours);
    this.baseUrl = baseUrl;
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }

  @Transactional
  public void startVerification(UserModel user) {
    tokens.consumeAllActive(user.getId(), Instant.now());

    String raw = SecureTokens.generate();
    EmailVerificationTokenModel token = new EmailVerificationTokenModel();
    token.setUserId(user.getId());
    token.setTokenHash(SecureTokens.hash(raw));
    token.setExpiresAt(Instant.now().plus(ttl));
    tokens.save(token);

    String link = baseUrl + "/verify-email?token=" + raw;
    boolean emailChange = user.getPendingEmail() != null && !user.getPendingEmail().isBlank();
    String destination = emailChange ? user.getPendingEmail() : user.getEmail();
    String subject =
        emailChange
            ? message("user.profile.email.change.subject")
            : message("auth.email.verification.subject");
    String template = emailChange ? "email/email-change" : "email/verification";
    mail.send(destination, subject, template, Map.of("name", user.getName(), "link", link));
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
}
