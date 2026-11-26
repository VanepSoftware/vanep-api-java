package br.com.vanep.job.repository;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.job.enums.JobStatus;
import br.com.vanep.job.enums.JobType;
import br.com.vanep.job.model.FailedJobModel;
import br.com.vanep.job.model.JobModel;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Limit;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class JobRepositoryTest {

  private static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");

  @Autowired private JobRepository jobs;
  @Autowired private FailedJobRepository failedJobs;
  @Autowired private TransactionTemplate transaction;

  @Test
  void claimsOnlyPendingDueJobsOldestFirstUpToTheLimit() {
    JobModel older = jobs.save(job(JobStatus.PENDING, NOW.minusSeconds(30)));
    JobModel newer = jobs.save(job(JobStatus.PENDING, NOW.minusSeconds(10)));
    jobs.save(job(JobStatus.PENDING, NOW.minusSeconds(5)));
    jobs.save(job(JobStatus.PENDING, NOW.plusSeconds(60)));
    jobs.save(job(JobStatus.PROCESSING, NOW.minusSeconds(60)));

    List<String> claimed =
        transaction.execute(
            status ->
                jobs.findDueForClaim(NOW, Limit.of(2)).stream()
                    .map(job -> job.getToken())
                    .toList());

    assertThat(claimed).containsExactly(older.getToken(), newer.getToken());
  }

  @Test
  void findsProcessingJobsLockedBeforeTheCutoff() {
    JobModel stuck = job(JobStatus.PROCESSING, NOW.minusSeconds(600));
    stuck.setLockedAt(NOW.minus(Duration.ofMinutes(10)));
    jobs.save(stuck);
    JobModel running = job(JobStatus.PROCESSING, NOW.minusSeconds(60));
    running.setLockedAt(NOW.minusSeconds(30));
    jobs.save(running);
    jobs.save(job(JobStatus.PENDING, NOW.minusSeconds(600)));

    List<String> found =
        transaction.execute(
            status ->
                jobs.findStuck(NOW.minus(Duration.ofMinutes(5)), Limit.of(10)).stream()
                    .map(job -> job.getToken())
                    .toList());

    assertThat(found).containsExactly(stuck.getToken());
  }

  @Test
  void findsRowsDueForRetryWithoutAQueuedJobEarliestFirst() {
    FailedJobModel dueLongAgo = failedJobs.save(failed("long-ago", NOW.minusSeconds(7200)));
    FailedJobModel dueNow = failedJobs.save(failed("now", NOW));
    failedJobs.save(failed("recent", NOW.minusSeconds(60)));
    failedJobs.save(failed("future", NOW.plusSeconds(60)));
    failedJobs.save(failed("no-retry", null));
    failedJobs.save(failed("queued", NOW.minusSeconds(9000)));
    JobModel queued = job(JobStatus.PENDING, NOW);
    queued.setToken("queued");
    jobs.save(queued);

    List<String> due =
        transaction.execute(
            status ->
                failedJobs.findDueForRetry(NOW, Limit.of(10)).stream()
                    .map(failed -> failed.getToken())
                    .toList());

    assertThat(due).containsExactly(dueLongAgo.getToken(), "recent", dueNow.getToken());
  }

  @Test
  void deletesAFailedJobByToken() {
    failedJobs.save(failed("gone", null));
    failedJobs.save(failed("kept", null));

    int deleted = transaction.execute(status -> failedJobs.deleteByToken("gone"));

    assertThat(deleted).isOne();
    assertThat(failedJobs.findByToken("gone")).isEmpty();
    assertThat(failedJobs.findByToken("kept")).isPresent();
  }

  @Test
  void storesThePayloadAsJson() {
    JobModel saved = job(JobStatus.PENDING, NOW);
    saved.setPayload(Map.of("notificationToken", "n-1", "fcmTokens", List.of("a", "b")));
    jobs.save(saved);

    JobModel loaded = jobs.findById(saved.getId()).orElseThrow();

    assertThat(loaded.getPayload())
        .containsEntry("notificationToken", "n-1")
        .containsEntry("fcmTokens", List.of("a", "b"));
  }

  private static JobModel job(JobStatus status, Instant runAt) {
    JobModel job = new JobModel();
    job.setType(JobType.SEND_PUSH);
    job.setPayload(Map.of());
    job.setStatus(status);
    job.setMaxAttempts(JobType.SEND_PUSH.maxAttempts());
    job.setRunAt(runAt);
    return job;
  }

  private static FailedJobModel failed(String token, Instant retryAt) {
    FailedJobModel failed = new FailedJobModel();
    failed.setToken(token);
    failed.setType(JobType.SEND_PUSH.name());
    failed.setPayload(Map.of());
    failed.setAttempts(5);
    failed.setException("boom");
    failed.setFailedAt(NOW.minusSeconds(3600));
    failed.setRetryAt(retryAt);
    return failed;
  }
}
