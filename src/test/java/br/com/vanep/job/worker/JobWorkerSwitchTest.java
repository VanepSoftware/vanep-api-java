package br.com.vanep.job.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import br.com.vanep.job.handler.JobHandlerRegistry;
import br.com.vanep.job.service.FailedJobService;
import br.com.vanep.job.service.JobService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class JobWorkerSwitchTest {

  private final ApplicationContextRunner context =
      new ApplicationContextRunner()
          .withInitializer(
              started ->
                  started
                      .getBeanFactory()
                      .setConversionService(ApplicationConversionService.getSharedInstance()))
          .withUserConfiguration(JobWorker.class, FailedJobReplayer.class)
          .withBean(JobService.class, () -> mock(JobService.class))
          .withBean(FailedJobService.class, () -> mock(FailedJobService.class))
          .withBean(JobHandlerRegistry.class, () -> mock(JobHandlerRegistry.class))
          .withBean(
              TransactionTemplate.class,
              () -> new TransactionTemplate(mock(PlatformTransactionManager.class)))
          .withPropertyValues(
              "vanep.worker.batch-size=20",
              "vanep.worker.lock-timeout=PT5M",
              "vanep.worker.replay.batch-size=20");

  @Test
  void withTheWorkerOffNothingClaimsOrReplaysJobs() {
    context
        .withPropertyValues("vanep.worker.enabled=false")
        .run(
            started -> {
              assertThat(started).doesNotHaveBean(JobWorker.class);
              assertThat(started).doesNotHaveBean(FailedJobReplayer.class);
            });
  }

  @Test
  void withTheWorkerOnBothTasksAreRegistered() {
    context
        .withPropertyValues("vanep.worker.enabled=true")
        .run(
            started -> {
              assertThat(started).hasSingleBean(JobWorker.class);
              assertThat(started).hasSingleBean(FailedJobReplayer.class);
            });
  }

  @Test
  void theReplayerUsesItsBatchSize() {
    FailedJobService failedJobs = mock(FailedJobService.class);

    new FailedJobReplayer(failedJobs, 20).replayFailedJobs();

    verify(failedJobs).replayFailedJobs(20);
  }
}
