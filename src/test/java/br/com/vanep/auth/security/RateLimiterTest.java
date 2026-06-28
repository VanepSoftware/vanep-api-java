package br.com.vanep.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RateLimiterTest {

  @Test
  void allowsUpToCapacityThenBlocks() {
    RateLimiter limiter = new RateLimiter(true, 2, 60);
    assertThat(limiter.tryAcquire("ip")).isTrue();
    assertThat(limiter.tryAcquire("ip")).isTrue();
    assertThat(limiter.tryAcquire("ip")).isFalse();
  }

  @Test
  void differentKeysAreIndependent() {
    RateLimiter limiter = new RateLimiter(true, 1, 60);
    assertThat(limiter.tryAcquire("a")).isTrue();
    assertThat(limiter.tryAcquire("b")).isTrue();
    assertThat(limiter.tryAcquire("a")).isFalse();
  }

  @Test
  void disabledAlwaysAllows() {
    RateLimiter limiter = new RateLimiter(false, 1, 60);
    assertThat(limiter.tryAcquire("ip")).isTrue();
    assertThat(limiter.tryAcquire("ip")).isTrue();
  }

  @Test
  void expiredWindowResets() {
    RateLimiter limiter = new RateLimiter(true, 1, 0);
    assertThat(limiter.tryAcquire("ip")).isTrue();
    assertThat(limiter.tryAcquire("ip")).isTrue();
  }
}
