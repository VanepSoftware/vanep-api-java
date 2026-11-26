package br.com.vanep.job.enums;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class JobTypeTest {

  @Test
  void thePushBackoffDoublesFromTenSeconds() {
    assertThat(JobType.SEND_PUSH.backoffAfter(1)).isEqualTo(Duration.ofSeconds(10));
    assertThat(JobType.SEND_PUSH.backoffAfter(2)).isEqualTo(Duration.ofSeconds(20));
    assertThat(JobType.SEND_PUSH.backoffAfter(3)).isEqualTo(Duration.ofSeconds(40));
    assertThat(JobType.SEND_PUSH.backoffAfter(4)).isEqualTo(Duration.ofSeconds(80));
  }

  @Test
  void thePushBackoffIsCappedAtTwoMinutes() {
    assertThat(JobType.SEND_PUSH.backoffAfter(5)).isEqualTo(Duration.ofMinutes(2));
    assertThat(JobType.SEND_PUSH.backoffAfter(1000)).isEqualTo(Duration.ofMinutes(2));
  }

  @Test
  void thePushGetsFiveAttempts() {
    assertThat(JobType.SEND_PUSH.maxAttempts()).isEqualTo(5);
  }
}
