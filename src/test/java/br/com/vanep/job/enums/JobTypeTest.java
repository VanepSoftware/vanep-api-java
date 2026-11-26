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
  void anEmailHasASingleAttemptInTheQueue() {
    assertThat(JobType.SEND_EMAIL.maxAttempts()).isOne();
  }

  @Test
  void anEmailIsReplayedAfter15MinutesThen1HourThen6HoursAndNoMore() {
    assertThat(JobType.SEND_EMAIL.replayDelayAfter(0)).contains(Duration.ofMinutes(15));
    assertThat(JobType.SEND_EMAIL.replayDelayAfter(1)).contains(Duration.ofHours(1));
    assertThat(JobType.SEND_EMAIL.replayDelayAfter(2)).contains(Duration.ofHours(6));
    assertThat(JobType.SEND_EMAIL.replayDelayAfter(3)).isEmpty();
  }

  @Test
  void aPushIsReplayedEveryTwoHoursFiveTimes() {
    for (int failedReplays = 0; failedReplays < 5; failedReplays++) {
      assertThat(JobType.SEND_PUSH.replayDelayAfter(failedReplays)).contains(Duration.ofHours(2));
    }
    assertThat(JobType.SEND_PUSH.replayDelayAfter(5)).isEmpty();
  }

  @Test
  void thePushGetsFiveAttempts() {
    assertThat(JobType.SEND_PUSH.maxAttempts()).isEqualTo(5);
  }
}
