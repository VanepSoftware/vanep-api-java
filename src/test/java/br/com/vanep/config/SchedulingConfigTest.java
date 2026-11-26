package br.com.vanep.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import br.com.vanep.proposal.service.ProposalService;
import br.com.vanep.servicerequest.service.ServiceRequestService;
import br.com.vanep.shared.service.ExpirySweepService;
import java.time.Clock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class SchedulingConfigTest {

  private final ApplicationContextRunner context =
      new ApplicationContextRunner()
          .withInitializer(
              started ->
                  started
                      .getBeanFactory()
                      .setConversionService(ApplicationConversionService.getSharedInstance()))
          .withUserConfiguration(SchedulingConfig.class, ExpirySweepService.class)
          .withBean(Clock.class, Clock::systemUTC)
          .withBean(ProposalService.class, () -> mock(ProposalService.class))
          .withBean(ServiceRequestService.class, () -> mock(ServiceRequestService.class))
          .withPropertyValues(
              "vanep.expiry-sweep.interval=PT1M", "vanep.expiry-sweep.batch-size=200");

  @Test
  void schedulingStaysOnWhenTheExpirySweepIsOff() {
    context
        .withPropertyValues("vanep.expiry-sweep.enabled=false")
        .run(
            started -> {
              assertThat(started).hasSingleBean(ThreadPoolTaskScheduler.class);
              assertThat(started).doesNotHaveBean(ExpirySweepService.class);
            });
  }

  @Test
  void theExpirySweepRunsOnlyWhenItsOwnSwitchIsOn() {
    context
        .withPropertyValues("vanep.expiry-sweep.enabled=true")
        .run(started -> assertThat(started).hasSingleBean(ExpirySweepService.class));
  }

  @Test
  void theSchedulerHasTwoThreads() {
    context
        .withPropertyValues("vanep.expiry-sweep.enabled=false")
        .run(
            started ->
                assertThat(
                        started
                            .getBean(ThreadPoolTaskScheduler.class)
                            .getScheduledThreadPoolExecutor()
                            .getCorePoolSize())
                    .isEqualTo(2));
  }

  @Test
  void closingTheContextLetsTheRunningTaskFinish() throws InterruptedException {
    CountDownLatch running = new CountDownLatch(1);
    AtomicBoolean finished = new AtomicBoolean();
    AtomicBoolean interrupted = new AtomicBoolean();

    context
        .withPropertyValues("vanep.expiry-sweep.enabled=false")
        .run(
            started -> {
              started
                  .getBean(ThreadPoolTaskScheduler.class)
                  .execute(
                      () -> {
                        running.countDown();
                        try {
                          Thread.sleep(500);
                          finished.set(true);
                        } catch (InterruptedException stopped) {
                          interrupted.set(true);
                          Thread.currentThread().interrupt();
                        }
                      });
              assertThat(running.await(5, TimeUnit.SECONDS)).isTrue();
            });

    assertThat(interrupted).isFalse();
    assertThat(finished).isTrue();
  }
}
