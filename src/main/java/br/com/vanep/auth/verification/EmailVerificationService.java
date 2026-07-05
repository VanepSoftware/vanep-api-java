package br.com.vanep.auth.verification;

import br.com.vanep.auth.mail.MailService;
import br.com.vanep.auth.token.SecureTokens;
import br.com.vanep.auth.verification.model.EmailVerificationTokenModel;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.model.UserModel;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailVerificationService {

  private final EmailVerificationTokenRepository tokens;
  private final UserRepository users;
  private final MailService mail;
  private final Duration ttl;
  private final String baseUrl;

  public EmailVerificationService(
      EmailVerificationTokenRepository tokens,
      UserRepository users,
      MailService mail,
      @Value("${vanep.mail.verification-ttl-hours:24}") long ttlHours,
      @Value("${vanep.app.base-url:http://localhost:8080}") String baseUrl) {
    this.tokens = tokens;
    this.users = users;
    this.mail = mail;
    this.ttl = Duration.ofHours(ttlHours);
    this.baseUrl = baseUrl;
  }

  @Transactional
  public void startVerification(UserModel user) {
    String raw = SecureTokens.generate();
    EmailVerificationTokenModel token = new EmailVerificationTokenModel();
    token.setUserId(user.getId());
    token.setTokenHash(SecureTokens.hash(raw));
    token.setExpiresAt(Instant.now().plus(ttl));
    tokens.save(token);

    String link = baseUrl + "/verify-email?token=" + raw;
    mail.send(
        user.getEmail(),
        "Confirme seu e-mail — Vanep",
        "email/verification",
        Map.of("name", user.getName(), "link", link));
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
    Optional<UserModel> user = users.findById(token.getUserId());
    if (user.isEmpty()) {
      return false;
    }
    user.get().setVerified(true);
    token.setConsumedAt(Instant.now());
    return true;
  }

  @Transactional
  public void resend(String email) {
    users.findByEmail(email).filter(user -> !user.isVerified()).ifPresent(this::startVerification);
  }
}
