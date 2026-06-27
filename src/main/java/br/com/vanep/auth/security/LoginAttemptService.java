package br.com.vanep.auth.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Bloqueio temporário por conta após excesso de tentativas de login (defesa contra brute-force /
 * credential stuffing). Em memória: para múltiplas instâncias migrar para um store compartilhado
 * (Redis). A janela é medida a partir da última falha; um login bem-sucedido zera o contador.
 */
@Service
public class LoginAttemptService {

  private static final int MAX_TRACKED_KEYS = 50_000;

  private final int maxAttempts;
  private final Duration lockDuration;
  private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

  public LoginAttemptService(
      @Value("${vanep.security.login.max-attempts:5}") int maxAttempts,
      @Value("${vanep.security.login.lock-minutes:15}") long lockMinutes) {
    this.maxAttempts = maxAttempts;
    this.lockDuration = Duration.ofMinutes(lockMinutes);
  }

  public void loginFailed(String key) {
    if (key == null || key.isBlank()) {
      return;
    }
    purgeIfTooLarge();
    String normalized = key.toLowerCase();
    Instant now = Instant.now();
    attempts.compute(
        normalized,
        (k, current) ->
            current == null || current.isExpired(now, lockDuration)
                ? new Attempt(1, now)
                : new Attempt(current.count() + 1, now));
  }

  public void loginSucceeded(String key) {
    if (key != null && !key.isBlank()) {
      attempts.remove(key.toLowerCase());
    }
  }

  public boolean isBlocked(String key) {
    if (key == null || key.isBlank()) {
      return false;
    }
    Attempt attempt = attempts.get(key.toLowerCase());
    if (attempt == null) {
      return false;
    }
    if (attempt.isExpired(Instant.now(), lockDuration)) {
      attempts.remove(key.toLowerCase());
      return false;
    }
    return attempt.count() >= maxAttempts;
  }

  private void purgeIfTooLarge() {
    if (attempts.size() <= MAX_TRACKED_KEYS) {
      return;
    }
    Instant now = Instant.now();
    attempts.values().removeIf(attempt -> attempt.isExpired(now, lockDuration));
  }

  private record Attempt(int count, Instant lastFailure) {
    boolean isExpired(Instant now, Duration lockDuration) {
      return now.isAfter(lastFailure.plus(lockDuration));
    }
  }
}
