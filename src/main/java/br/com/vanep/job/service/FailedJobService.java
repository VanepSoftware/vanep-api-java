package br.com.vanep.job.service;

import br.com.vanep.job.model.FailedJobModel;
import br.com.vanep.job.model.JobModel;
import br.com.vanep.job.repository.FailedJobRepository;
import br.com.vanep.job.repository.JobRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FailedJobService {

  static final int REPLAY_MAX_ATTEMPTS = 1;

  private final FailedJobRepository failedJobs;
  private final JobRepository jobs;
  private final Clock clock;

  public FailedJobService(FailedJobRepository failedJobs, JobRepository jobs, Clock clock) {
    this.failedJobs = failedJobs;
    this.jobs = jobs;
    this.clock = clock;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void recordFailure(JobModel job, int attempts, String exception, boolean permanent) {
    Instant now = clock.instant();
    FailedJobModel failed =
        failedJobs
            .findByToken(job.getToken())
            .map(
                replayed -> {
                  replayed.setTries(replayed.getTries() + 1);
                  return replayed;
                })
            .orElseGet(() -> newFailedJob(job));
    failed.setPayload(job.getPayload());
    failed.setAttempts(attempts);
    failed.setException(exception);
    failed.setPermanent(failed.isPermanent() || permanent);
    failed.setFailedAt(now);
    failed.setRetryAt(nextRetryAt(job, failed, now));
    failedJobs.save(failed);
  }

  // The delay grows with each failed replay; a permanent failure, an unknown type or an exhausted
  // schedule leaves retry_at null, and the row stays in failed_jobs for manual analysis.
  static Instant nextRetryAt(JobModel job, FailedJobModel failed, Instant now) {
    if (failed.isPermanent()) {
      return null;
    }
    return job.findJobType()
        .flatMap(type -> type.replayDelayAfter(failed.getTries()))
        .map(now::plus)
        .orElse(null);
  }

  FailedJobModel newFailedJob(JobModel job) {
    FailedJobModel failed = new FailedJobModel();
    failed.setToken(job.getToken());
    failed.setType(job.getType());
    return failed;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void clearFailure(String token) {
    failedJobs.deleteByToken(token);
  }

  // Only feeds the queue: the normal worker runs the replayed jobs at its own pace, one attempt
  // each, so a large backlog of failures never turns into a burst of work.
  @Transactional
  public int replayFailedJobs(int batchSize) {
    Instant now = clock.instant();
    List<FailedJobModel> due = failedJobs.findDueForRetry(now, Limit.of(batchSize));
    for (FailedJobModel failed : due) {
      jobs.save(replayJob(failed, now));
      failed.setLastTriedAt(now);
      failed.setRetryAt(null);
    }
    return due.size();
  }

  JobModel replayJob(FailedJobModel failed, Instant now) {
    JobModel job = new JobModel();
    job.setToken(failed.getToken());
    job.setType(failed.getType());
    job.setPayload(failed.getPayload());
    job.setMaxAttempts(REPLAY_MAX_ATTEMPTS);
    job.setRunAt(now);
    return job;
  }
}
