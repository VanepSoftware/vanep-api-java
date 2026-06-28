package br.com.vanep.auth.password;

import br.com.vanep.auth.mail.MailService;
import br.com.vanep.auth.token.SecureTokens;
import br.com.vanep.user.User;
import br.com.vanep.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {

  private final PasswordResetTokenRepository tokens;
  private final UserRepository users;
  private final MailService mail;
  private final PasswordEncoder passwordEncoder;
  private final Duration ttl;
  private final String baseUrl;

  public PasswordResetService(
      PasswordResetTokenRepository tokens,
      UserRepository users,
      MailService mail,
      PasswordEncoder passwordEncoder,
      @Value("${vanep.mail.reset-ttl-minutes:60}") long ttlMinutes,
      @Value("${vanep.app.base-url:http://localhost:8080}") String baseUrl) {
    this.tokens = tokens;
    this.users = users;
    this.mail = mail;
    this.passwordEncoder = passwordEncoder;
    this.ttl = Duration.ofMinutes(ttlMinutes);
    this.baseUrl = baseUrl;
  }

  @Transactional
  public void requestReset(String email) {
    users
        .findByEmailAndDeletedAtIsNull(email)
        .filter(user -> user.getPassword() != null && !user.getPassword().isBlank())
        .ifPresent(
            user -> {
              tokens.consumeAllActive(user.getId(), Instant.now());
              String raw = SecureTokens.generate();
              PasswordResetToken token = new PasswordResetToken();
              token.setUserId(user.getId());
              token.setTokenHash(SecureTokens.hash(raw));
              token.setExpiresAt(Instant.now().plus(ttl));
              tokens.save(token);

              String link = baseUrl + "/reset-password?token=" + raw;
              mail.send(
                  user.getEmail(),
                  "Redefinir sua senha — Vanep",
                  "email/password-reset",
                  Map.of("name", user.getName(), "link", link));
            });
  }

  public boolean isValidToken(String rawToken) {
    return findValid(rawToken).isPresent();
  }

  @Transactional
  public boolean reset(String rawToken, String newPassword) {
    Optional<PasswordResetToken> maybe = findValid(rawToken);
    if (maybe.isEmpty()) {
      return false;
    }
    PasswordResetToken token = maybe.get();
    Optional<User> user = users.findById(token.getUserId());
    if (user.isEmpty()) {
      return false;
    }
    user.get().setPassword(passwordEncoder.encode(newPassword));
    token.setConsumedAt(Instant.now());
    return true;
  }

  private Optional<PasswordResetToken> findValid(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      return Optional.empty();
    }
    return tokens
        .findByTokenHash(SecureTokens.hash(rawToken))
        .filter(
            token -> token.getConsumedAt() == null && token.getExpiresAt().isAfter(Instant.now()));
  }
}
