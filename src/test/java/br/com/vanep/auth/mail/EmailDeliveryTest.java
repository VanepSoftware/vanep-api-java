package br.com.vanep.auth.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import br.com.vanep.job.enums.JobType;
import br.com.vanep.job.handler.JobHandlerRegistry;
import br.com.vanep.job.model.FailedJobModel;
import br.com.vanep.job.model.JobModel;
import br.com.vanep.job.repository.FailedJobRepository;
import br.com.vanep.job.repository.JobRepository;
import br.com.vanep.job.service.FailedJobService;
import br.com.vanep.job.service.JobService;
import br.com.vanep.job.worker.JobWorker;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class EmailDeliveryTest {

  @MockitoSpyBean private SendEmailJobHandler handler;
  @Autowired private MailService mail;
  @Autowired private JobService jobService;
  @Autowired private FailedJobService failedJobService;
  @Autowired private JobHandlerRegistry handlers;
  @Autowired private JobRepository jobs;
  @Autowired private FailedJobRepository failedJobs;
  @Autowired private TransactionTemplate transaction;

  @Test
  void anEmailIsOnlyQueuedInsideTheCallersTransaction() {
    assertThatThrownBy(() -> sendWelcome()).isInstanceOf(IllegalTransactionStateException.class);

    transaction.executeWithoutResult(status -> sendWelcome());

    JobModel queued = jobs.findAll().getFirst();
    assertThat(queued.findJobType()).contains(JobType.SEND_EMAIL);
    assertThat(queued.getMaxAttempts()).isOne();
    assertThat(queued.getPayload()).containsEntry("to", "ana@vanep.com");
  }

  @Test
  void theWorkerSendsTheEmailAndDropsTheJob() {
    transaction.executeWithoutResult(status -> sendWelcome());

    newWorker().runCycle();

    verify(handler).perform(any());
    assertThat(jobs.count()).isZero();
    assertThat(failedJobs.count()).isZero();
  }

  @Test
  void aFailedSendGoesStraightToFailedJobsAndIsRetriedOnTheEmailSchedule() {
    doThrow(new MailSendException("smtp down")).when(handler).perform(any());
    transaction.executeWithoutResult(status -> sendWelcome());

    newWorker().runCycle();

    assertThat(jobs.count()).isZero();
    FailedJobModel failed = failedJobs.findAll().getFirst();
    assertThat(failed.getTries()).isZero();
    assertThat(failed.getRetryAt())
        .isCloseTo(inFromNow(Duration.ofMinutes(15)), within(Duration.ofMinutes(1)));

    makeDue(failed);
    assertThat(failedJobService.replayFailedJobs(20)).isOne();
    newWorker().runCycle();

    FailedJobModel retried = failedJobs.findAll().getFirst();
    assertThat(retried.getTries()).isOne();
    assertThat(retried.getRetryAt())
        .isCloseTo(inFromNow(Duration.ofHours(1)), within(Duration.ofMinutes(1)));
  }

  private void sendWelcome() {
    mail.send("ana@vanep.com", "Bem-vinda", "email/verification", Map.of("name", "Ana"));
  }

  private void makeDue(FailedJobModel failed) {
    failed.setRetryAt(Instant.now().minusSeconds(1));
    failedJobs.save(failed);
  }

  private static Instant inFromNow(Duration delay) {
    return Instant.now().plus(delay);
  }

  private JobWorker newWorker() {
    return new JobWorker(jobService, handlers, transaction, 20, Duration.ofMinutes(5));
  }
}
