package br.com.vanep.job.service;

import br.com.vanep.job.enums.JobStatus;
import br.com.vanep.job.enums.JobType;
import br.com.vanep.job.handler.PermanentJobFailureException;
import br.com.vanep.job.model.JobModel;
import br.com.vanep.job.repository.JobRepository;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
public class JobService {

  private static final TypeReference<Map<String, Object>> PAYLOAD_MAP = new TypeReference<>() {};

  private final JobRepository jobs;
  private final FailedJobService failedJobs;
  private final JsonMapper jsonMapper;
  private final Clock clock;

  public JobService(
      JobRepository jobs, FailedJobService failedJobs, JsonMapper jsonMapper, Clock clock) {
    this.jobs = jobs;
    this.failedJobs = failedJobs;
    this.jsonMapper = jsonMapper;
    this.clock = clock;
  }

  // MANDATORY: a job must commit or roll back with the change that asked for it.
  @Transactional(propagation = Propagation.MANDATORY)
  public JobModel enqueue(JobType type, Object payload) {
    return enqueue(type, payload, clock.instant());
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public JobModel enqueue(JobType type, Object payload, Instant runAt) {
    JobModel job = new JobModel();
    job.setType(type);
    job.setPayload(toPayloadMap(payload));
    job.setMaxAttempts(type.maxAttempts());
    job.setRunAt(runAt);
    return jobs.save(job);
  }

  Map<String, Object> toPayloadMap(Object payload) {
    return jsonMapper.convertValue(payload, PAYLOAD_MAP);
  }

  public <T> T readPayload(JobModel job, Class<T> payloadType) {
    try {
      return jsonMapper.convertValue(job.getPayload(), payloadType);
    } catch (JacksonException unreadable) {
      throw new PermanentJobFailureException(
          "Payload of job %s is not a %s".formatted(job.getToken(), payloadType.getSimpleName()),
          unreadable);
    }
  }

  @Transactional
  public List<JobModel> claimDueJobs(String workerId, int batchSize) {
    Instant now = clock.instant();
    List<JobModel> due = jobs.findDueForClaim(now, Limit.of(batchSize));
    for (JobModel job : due) {
      job.setStatus(JobStatus.PROCESSING);
      job.setLockedAt(now);
      job.setLockedBy(workerId);
    }
    return due;
  }

  @Transactional
  public void completeJob(Long jobId) {
    jobs.findById(jobId)
        .ifPresent(
            job -> {
              failedJobs.clearFailure(job.getToken());
              jobs.delete(job);
            });
  }

  @Transactional
  public void registerFailedAttempt(Long jobId, Throwable failure, Object replacementPayload) {
    jobs.findById(jobId).ifPresent(job -> registerFailedAttempt(job, failure, replacementPayload));
  }

  void registerFailedAttempt(JobModel job, Throwable failure, Object replacementPayload) {
    int attempts = job.getAttempts() + 1;
    if (replacementPayload != null) {
      job.setPayload(toPayloadMap(replacementPayload));
    }
    Optional<JobType> type = job.findJobType();
    if (type.isEmpty()) {
      moveToFailedJobs(job, attempts, failure, true);
      return;
    }
    if (attempts >= job.getMaxAttempts()) {
      moveToFailedJobs(job, attempts, failure, false);
      return;
    }
    job.setAttempts(attempts);
    job.setLastError(failure.toString());
    job.setStatus(JobStatus.PENDING);
    job.setLockedAt(null);
    job.setLockedBy(null);
    job.setRunAt(clock.instant().plus(type.get().backoffAfter(attempts)));
  }

  @Transactional
  public void failPermanently(Long jobId, Throwable failure) {
    jobs.findById(jobId)
        .ifPresent(job -> moveToFailedJobs(job, job.getAttempts() + 1, failure, true));
  }

  void moveToFailedJobs(JobModel job, int attempts, Throwable failure, boolean permanent) {
    failedJobs.recordFailure(job, attempts, describe(failure), permanent);
    jobs.delete(job);
  }

  // A crashed worker leaves its jobs PROCESSING; counting that as an attempt keeps a job that
  // kills the worker from looping forever.
  @Transactional
  public int recoverStuckJobs(Duration lockTimeout, int batchSize) {
    Instant cutoff = clock.instant().minus(lockTimeout);
    List<JobModel> stuck = jobs.findStuck(cutoff, Limit.of(batchSize));
    for (JobModel job : stuck) {
      registerFailedAttempt(job, new StuckJobException(job, lockTimeout), null);
    }
    return stuck.size();
  }

  static String describe(Throwable failure) {
    StringWriter trace = new StringWriter();
    failure.printStackTrace(new PrintWriter(trace));
    return trace.toString();
  }

  static class StuckJobException extends RuntimeException {

    StuckJobException(JobModel job, Duration lockTimeout) {
      super(
          "Job %s stayed PROCESSING on %s for more than %s"
              .formatted(job.getToken(), job.getLockedBy(), lockTimeout));
    }
  }
}
