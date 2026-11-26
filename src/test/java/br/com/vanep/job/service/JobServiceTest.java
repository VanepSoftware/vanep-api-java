package br.com.vanep.job.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.vanep.job.enums.JobStatus;
import br.com.vanep.job.enums.JobType;
import br.com.vanep.job.handler.PermanentJobFailureException;
import br.com.vanep.job.model.FailedJobModel;
import br.com.vanep.job.model.JobModel;
import br.com.vanep.job.repository.FailedJobRepository;
import br.com.vanep.job.repository.JobRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class JobServiceTest {

  record Payload(String notificationToken, List<String> fcmTokens) {}

  @Autowired private JobService service;
  @Autowired private JobRepository jobs;
  @Autowired private FailedJobRepository failedJobs;
  @Autowired private TransactionTemplate transaction;

  @Test
  void anEnqueuedJobCommitsWithItsTransaction() {
    Instant before = Instant.now();

    transaction.executeWithoutResult(
        status -> service.enqueue(JobType.SEND_PUSH, new Payload("n-1", null)));

    JobModel stored = jobs.findAll().getFirst();
    assertThat(stored.getStatus()).isEqualTo(JobStatus.PENDING);
    assertThat(stored.getAttempts()).isZero();
    assertThat(stored.getMaxAttempts()).isEqualTo(5);
    assertThat(stored.getRunAt()).isBetween(before, Instant.now());
    assertThat(stored.getPayload()).containsEntry("notificationToken", "n-1");
  }

  @Test
  void aRolledBackTransactionLeavesNoJob() {
    transaction.executeWithoutResult(
        status -> {
          service.enqueue(JobType.SEND_PUSH, new Payload("n-1", null));
          status.setRollbackOnly();
        });

    assertThat(jobs.count()).isZero();
  }

  @Test
  void enqueueingOutsideATransactionIsRejected() {
    assertThatThrownBy(() -> service.enqueue(JobType.SEND_PUSH, new Payload("n-1", null)))
        .isInstanceOf(IllegalTransactionStateException.class);
    assertThat(jobs.count()).isZero();
  }

  @Test
  void aDelayedJobKeepsItsRunAtAndIsNotClaimedEarly() {
    Instant later = Instant.now().plus(Duration.ofHours(1));
    transaction.executeWithoutResult(
        status -> service.enqueue(JobType.SEND_PUSH, new Payload("n-1", null), later));

    assertThat(service.claimDueJobs("worker-a", 10)).isEmpty();
    assertThat(jobs.findAll().getFirst().getRunAt()).isEqualTo(later);
  }

  @Test
  void claimingMarksTheJobAsProcessingByTheWorker() {
    JobModel queued = enqueue();

    List<JobModel> claimed = service.claimDueJobs("worker-a", 10);

    assertThat(claimed).extracting(job -> job.getToken()).containsExactly(queued.getToken());
    JobModel stored = jobs.findById(queued.getId()).orElseThrow();
    assertThat(stored.getStatus()).isEqualTo(JobStatus.PROCESSING);
    assertThat(stored.getLockedBy()).isEqualTo("worker-a");
    assertThat(stored.getLockedAt()).isNotNull();
    assertThat(service.claimDueJobs("worker-b", 10)).isEmpty();
  }

  @Test
  void aCompletedJobIsDeleted() {
    JobModel queued = enqueue();

    service.completeJob(queued.getId());

    assertThat(jobs.count()).isZero();
    assertThat(failedJobs.count()).isZero();
  }

  @Test
  void aFailedAttemptReschedulesWithBackoffAndFreesTheJob() {
    JobModel queued = enqueue();
    service.claimDueJobs("worker-a", 10);
    Instant before = Instant.now();

    service.registerFailedAttempt(queued.getId(), new IllegalStateException("fcm down"), null);

    JobModel stored = jobs.findById(queued.getId()).orElseThrow();
    assertThat(stored.getStatus()).isEqualTo(JobStatus.PENDING);
    assertThat(stored.getAttempts()).isOne();
    assertThat(stored.getLastError()).contains("fcm down");
    assertThat(stored.getLockedBy()).isNull();
    assertThat(stored.getRunAt()).isBetween(before.plusSeconds(10), Instant.now().plusSeconds(10));
  }

  @Test
  void aRescheduledJobDoesNotHoldBackTheOthers() {
    JobModel failing = enqueue();
    service.claimDueJobs("worker-a", 10);
    service.registerFailedAttempt(failing.getId(), new IllegalStateException("fcm down"), null);
    JobModel next = enqueue();

    assertThat(service.claimDueJobs("worker-a", 10))
        .extracting(job -> job.getToken())
        .containsExactly(next.getToken());
  }

  @Test
  void aRetryCanNarrowThePayload() {
    JobModel queued = enqueue();

    service.registerFailedAttempt(
        queued.getId(), new IllegalStateException("partial"), new Payload("n-1", List.of("b")));

    assertThat(jobs.findById(queued.getId()).orElseThrow().getPayload())
        .containsEntry("fcmTokens", List.of("b"));
  }

  @Test
  void theLastAttemptMovesTheJobToFailedJobs() {
    JobModel queued = enqueue();
    for (int attempt = 1; attempt <= 5; attempt++) {
      service.registerFailedAttempt(queued.getId(), new IllegalStateException("boom"), null);
    }

    assertThat(jobs.count()).isZero();
    FailedJobModel failed = failedJobs.findByToken(queued.getToken()).orElseThrow();
    assertThat(failed.getAttempts()).isEqualTo(5);
    assertThat(failed.getTries()).isZero();
    assertThat(failed.isPermanent()).isFalse();
    assertThat(failed.getException()).contains("IllegalStateException: boom");
    assertThat(failed.getPayload()).containsEntry("notificationToken", "n-1");
  }

  @Test
  void aPermanentFailureSkipsTheRetries() {
    JobModel queued = enqueue();

    service.failPermanently(queued.getId(), new PermanentJobFailureException("user gone"));

    assertThat(jobs.count()).isZero();
    FailedJobModel failed = failedJobs.findByToken(queued.getToken()).orElseThrow();
    assertThat(failed.isPermanent()).isTrue();
    assertThat(failed.getAttempts()).isOne();
  }

  @Test
  void anUnreadablePayloadIsAPermanentFailure() {
    JobModel queued = enqueue();
    queued.setPayload(Map.of("fcmTokens", "not-a-list"));

    assertThatThrownBy(() -> service.readPayload(queued, Payload.class))
        .isInstanceOf(PermanentJobFailureException.class);
  }

  @Test
  void aJobStuckPastTheLockTimeoutCountsAsAFailedAttempt() {
    JobModel queued = enqueue();
    service.claimDueJobs("worker-a", 10);
    JobModel stuck = jobs.findById(queued.getId()).orElseThrow();
    stuck.setLockedAt(Instant.now().minus(Duration.ofMinutes(10)));
    jobs.save(stuck);

    int recovered = service.recoverStuckJobs(Duration.ofMinutes(5), 10);

    assertThat(recovered).isOne();
    JobModel stored = jobs.findById(queued.getId()).orElseThrow();
    assertThat(stored.getStatus()).isEqualTo(JobStatus.PENDING);
    assertThat(stored.getAttempts()).isOne();
    assertThat(stored.getLockedBy()).isNull();
    assertThat(stored.getLastError()).contains("worker-a");
  }

  @Test
  void aJobWithinTheLockTimeoutIsLeftAlone() {
    JobModel queued = enqueue();
    service.claimDueJobs("worker-a", 10);

    assertThat(service.recoverStuckJobs(Duration.ofMinutes(5), 10)).isZero();
    assertThat(jobs.findById(queued.getId()).orElseThrow().getStatus())
        .isEqualTo(JobStatus.PROCESSING);
  }

  @Test
  void aStuckJobWithNoAttemptsLeftMovesToFailedJobs() {
    JobModel queued = enqueue();
    JobModel stuck = jobs.findById(queued.getId()).orElseThrow();
    stuck.setAttempts(4);
    stuck.setStatus(JobStatus.PROCESSING);
    stuck.setLockedAt(Instant.now().minus(Duration.ofMinutes(10)));
    jobs.save(stuck);

    service.recoverStuckJobs(Duration.ofMinutes(5), 10);

    assertThat(jobs.count()).isZero();
    assertThat(failedJobs.findByToken(queued.getToken())).isPresent();
  }

  @Test
  void aStuckJobOfARemovedTypeFailsPermanently() {
    JobModel stuck = enqueue();
    stuck.setType("REMOVED_TYPE");
    stuck.setStatus(JobStatus.PROCESSING);
    stuck.setLockedAt(Instant.now().minus(Duration.ofMinutes(10)));
    jobs.save(stuck);

    service.recoverStuckJobs(Duration.ofMinutes(5), 10);

    assertThat(jobs.count()).isZero();
    FailedJobModel failed = failedJobs.findByToken(stuck.getToken()).orElseThrow();
    assertThat(failed.isPermanent()).isTrue();
    assertThat(failed.getType()).isEqualTo("REMOVED_TYPE");
  }

  private JobModel enqueue() {
    return transaction.execute(
        status -> service.enqueue(JobType.SEND_PUSH, new Payload("n-1", null)));
  }
}
