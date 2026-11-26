package br.com.vanep.job.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.job.enums.JobType;
import br.com.vanep.job.handler.PermanentJobFailureException;
import br.com.vanep.job.model.FailedJobModel;
import br.com.vanep.job.model.JobModel;
import br.com.vanep.job.repository.FailedJobRepository;
import br.com.vanep.job.repository.JobRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class FailedJobServiceTest {

  @Autowired private FailedJobService service;
  @Autowired private JobService jobService;
  @Autowired private JobRepository jobs;
  @Autowired private FailedJobRepository failedJobs;
  @Autowired private TransactionTemplate transaction;

  @Test
  void aReplaySendsBackOnlyTheBatchSizeWithOneAttemptEach() {
    for (int index = 0; index < 5; index++) {
      failedJobs.save(failed("failed-" + index));
    }

    int replayed = service.replayFailedJobs(2);

    assertThat(replayed).isEqualTo(2);
    List<JobModel> queued = jobs.findAll();
    assertThat(queued).hasSize(2);
    assertThat(queued).allSatisfy(job -> assertThat(job.getMaxAttempts()).isOne());
    assertThat(queued).allSatisfy(job -> assertThat(job.getAttempts()).isZero());
    assertThat(failedJobs.count()).isEqualTo(5);
  }

  @Test
  void aReplayedRowStaysAndIsNotSentAgainWhileItsJobIsQueued() {
    failedJobs.save(failed("only"));

    service.replayFailedJobs(10);
    int secondRun = service.replayFailedJobs(10);

    assertThat(secondRun).isZero();
    assertThat(jobs.count()).isOne();
    FailedJobModel row = failedJobs.findByToken("only").orElseThrow();
    assertThat(row.getLastTriedAt()).isNotNull();
    assertThat(row.getRetryAt()).isNull();
    assertThat(jobs.findAll().getFirst().getToken()).isEqualTo("only");
    assertThat(jobs.findAll().getFirst().getPayload()).containsEntry("notificationToken", "n-1");
  }

  @Test
  void aSuccessfulReplayRemovesTheFailedRow() {
    failedJobs.save(failed("only"));
    service.replayFailedJobs(10);
    JobModel replayed = jobs.findAll().getFirst();

    jobService.completeJob(replayed.getId());

    assertThat(jobs.count()).isZero();
    assertThat(failedJobs.count()).isZero();
  }

  @Test
  void aFailedReplayCountsATryOnTheSameRow() {
    failedJobs.save(failed("only"));
    service.replayFailedJobs(10);
    JobModel replayed = jobs.findAll().getFirst();

    jobService.registerFailedAttempt(replayed.getId(), new IllegalStateException("again"), null);

    assertThat(jobs.count()).isZero();
    assertThat(failedJobs.count()).isOne();
    FailedJobModel row = failedJobs.findByToken("only").orElseThrow();
    assertThat(row.getTries()).isOne();
    assertThat(row.getException()).contains("again");
    assertThat(row.isPermanent()).isFalse();
    assertThat(row.getRetryAt())
        .isBetween(
            Instant.now().plus(Duration.ofHours(2)).minusSeconds(60),
            Instant.now().plus(Duration.ofHours(2)));
  }

  @Test
  void aReplayThatFailsPermanentlyMarksTheRow() {
    failedJobs.save(failed("only"));
    service.replayFailedJobs(10);
    JobModel replayed = jobs.findAll().getFirst();

    jobService.failPermanently(replayed.getId(), new PermanentJobFailureException("gone"));

    FailedJobModel row = failedJobs.findByToken("only").orElseThrow();
    assertThat(row.isPermanent()).isTrue();
    assertThat(row.getTries()).isOne();
    assertThat(row.getRetryAt()).isNull();
    assertThat(service.replayFailedJobs(10)).isZero();
  }

  @Test
  void aRowIsNotReplayedBeforeItsRetryAt() {
    FailedJobModel later = failed("later");
    later.setRetryAt(Instant.now().plus(Duration.ofMinutes(10)));
    failedJobs.save(later);

    assertThat(service.replayFailedJobs(10)).isZero();
    assertThat(jobs.count()).isZero();
  }

  @Test
  void aRowWithoutRetryAtIsNeverReplayed() {
    FailedJobModel exhausted = failed("exhausted");
    exhausted.setRetryAt(null);
    failedJobs.save(exhausted);

    assertThat(service.replayFailedJobs(10)).isZero();
    assertThat(jobs.count()).isZero();
  }

  @Test
  void anEmailIsRetriedAfter15MinutesThen1HourThen6HoursAndThenKeptForGood() {
    JobModel email = emailJob();

    assertThat(failAndReadRetryDelay(email)).isEqualTo(Duration.ofMinutes(15));
    assertThat(failAndReadRetryDelay(email)).isEqualTo(Duration.ofHours(1));
    assertThat(failAndReadRetryDelay(email)).isEqualTo(Duration.ofHours(6));
    transaction.executeWithoutResult(status -> service.recordFailure(email, 1, "trace", false));

    FailedJobModel row = failedJobs.findByToken("email").orElseThrow();
    assertThat(row.getTries()).isEqualTo(3);
    assertThat(row.getRetryAt()).isNull();
    assertThat(row.isPermanent()).isFalse();
  }

  @Test
  void aPermanentFailureGetsNoRetryAt() {
    JobModel email = emailJob();

    transaction.executeWithoutResult(status -> service.recordFailure(email, 1, "trace", true));

    assertThat(failedJobs.findByToken("email").orElseThrow().getRetryAt()).isNull();
  }

  private Duration failAndReadRetryDelay(JobModel job) {
    Instant before = Instant.now();
    transaction.executeWithoutResult(status -> service.recordFailure(job, 1, "trace", false));
    FailedJobModel row = failedJobs.findByToken(job.getToken()).orElseThrow();
    return Duration.between(before, row.getRetryAt()).truncatedTo(ChronoUnit.MINUTES);
  }

  private static JobModel emailJob() {
    JobModel job = new JobModel();
    job.setToken("email");
    job.setType(JobType.SEND_EMAIL);
    job.setPayload(Map.of("to", "x@vanep.com"));
    return job;
  }

  @Test
  void aFirstFailureIsRecordedOnlyInsideATransaction() {
    JobModel job = new JobModel();
    job.setToken("fresh");
    job.setType(JobType.SEND_PUSH);
    job.setPayload(Map.of());

    transaction.executeWithoutResult(status -> service.recordFailure(job, 5, "trace", false));

    FailedJobModel row = failedJobs.findByToken("fresh").orElseThrow();
    assertThat(row.getTries()).isZero();
    assertThat(row.getAttempts()).isEqualTo(5);
    assertThat(row.getFailedAt()).isNotNull();
  }

  private static FailedJobModel failed(String token) {
    FailedJobModel failed = new FailedJobModel();
    failed.setToken(token);
    failed.setType(JobType.SEND_PUSH.name());
    failed.setPayload(Map.of("notificationToken", "n-1"));
    failed.setAttempts(5);
    failed.setException("boom");
    failed.setFailedAt(Instant.now().minusSeconds(3600));
    failed.setRetryAt(Instant.now().minusSeconds(60));
    return failed;
  }
}
