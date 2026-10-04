package br.com.vanep.driverrating.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.clientdriver.enums.RelationshipStatus;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;

class DriverRatingEligibilityPolicyTest {

  private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");

  private final DriverRatingEligibilityPolicy policy = new DriverRatingEligibilityPolicy();

  @Test
  void anActiveLinkCanRate() {
    assertThat(policy.isLinkActive(RelationshipStatus.ACTIVE)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(value = RelationshipStatus.class, mode = Mode.EXCLUDE, names = "ACTIVE")
  void aLinkThatIsNotActiveCannotRate(RelationshipStatus status) {
    assertThat(policy.isLinkActive(status)).isFalse();
  }

  @Test
  void aLinkCreatedFourMinutesAgoIsTooRecent() {
    assertThat(policy.isLinkOldEnough(NOW.minus(Duration.ofMinutes(4)), NOW)).isFalse();
  }

  @Test
  void aLinkCreatedExactlyFiveMinutesAgoIsOldEnough() {
    assertThat(policy.isLinkOldEnough(NOW.minus(Duration.ofMinutes(5)), NOW)).isTrue();
  }

  @Test
  void aLinkCreatedADayAgoIsOldEnough() {
    assertThat(policy.isLinkOldEnough(NOW.minus(Duration.ofDays(1)), NOW)).isTrue();
  }

  @Test
  void canRateRequiresAnActiveLinkOldEnough() {
    Instant longAgo = NOW.minus(Duration.ofDays(1));
    Instant justNow = NOW.minus(Duration.ofMinutes(1));

    assertThat(policy.canRate(RelationshipStatus.ACTIVE, longAgo, NOW)).isTrue();
    assertThat(policy.canRate(RelationshipStatus.ACTIVE, justNow, NOW)).isFalse();
    assertThat(policy.canRate(RelationshipStatus.INACTIVE, longAgo, NOW)).isFalse();
  }
}
