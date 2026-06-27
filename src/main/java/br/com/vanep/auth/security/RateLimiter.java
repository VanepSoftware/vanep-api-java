package br.com.vanep.auth.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Rate limit simples por chave (janela fixa, em memória). Usado pelo {@link RateLimitingFilter}
 * para limitar requisições por IP nos endpoints sensíveis. Para múltiplas instâncias migrar para um
 * store compartilhado (Redis).
 */
@Component
public class RateLimiter {

  private static final int MAX_TRACKED_KEYS = 100_000;

  private final boolean enabled;
  private final int capacity;
  private final Duration window;
  private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

  public RateLimiter(
      @Value("${vanep.security.rate-limit.enabled:true}") boolean enabled,
      @Value("${vanep.security.rate-limit.capacity:30}") int capacity,
      @Value("${vanep.security.rate-limit.window-seconds:60}") long windowSeconds) {
    this.enabled = enabled;
    this.capacity = capacity;
    this.window = Duration.ofSeconds(windowSeconds);
  }

  /** {@code true} se a requisição é permitida; {@code false} se estourou o limite da janela. */
  public boolean tryAcquire(String key) {
    if (!enabled) {
      return true;
    }
    Instant now = Instant.now();
    if (buckets.size() > MAX_TRACKED_KEYS) {
      buckets.values().removeIf(bucket -> bucket.isExpired(now, window));
    }
    Bucket bucket =
        buckets.compute(
            key,
            (k, current) ->
                current == null || current.isExpired(now, window) ? new Bucket(now) : current);
    return bucket.count().incrementAndGet() <= capacity;
  }

  private record Bucket(AtomicInteger count, Instant windowStart) {
    Bucket(Instant windowStart) {
      this(new AtomicInteger(0), windowStart);
    }

    boolean isExpired(Instant now, Duration window) {
      return now.isAfter(windowStart.plus(window));
    }
  }
}
