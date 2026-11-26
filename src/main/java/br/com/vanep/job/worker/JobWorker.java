package br.com.vanep.job.worker;

import br.com.vanep.job.handler.JobHandler;
import br.com.vanep.job.handler.JobHandlerRegistry;
import br.com.vanep.job.handler.JobRetryException;
import br.com.vanep.job.handler.PermanentJobFailureException;
import br.com.vanep.job.model.JobModel;
import br.com.vanep.job.service.JobService;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(name = "vanep.worker.enabled", havingValue = "true")
public class JobWorker {

  private static final Logger log = LoggerFactory.getLogger(JobWorker.class);

  private final JobService jobs;
  private final JobHandlerRegistry handlers;
  private final TransactionTemplate transaction;
  private final int batchSize;
  private final Duration lockTimeout;
  private final String workerId;

  public JobWorker(
      JobService jobs,
      JobHandlerRegistry handlers,
      TransactionTemplate transaction,
      @Value("${vanep.worker.batch-size}") int batchSize,
      @Value("${vanep.worker.lock-timeout}") Duration lockTimeout) {
    this.jobs = jobs;
    this.handlers = handlers;
    this.transaction = transaction;
    this.batchSize = batchSize;
    this.lockTimeout = lockTimeout;
    this.workerId = buildWorkerId();
  }

  static String buildWorkerId() {
    String host;
    try {
      host = InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException unknown) {
      host = "unknown-host";
    }
    return host + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  public String workerId() {
    return workerId;
  }

  // CD greps for this line to confirm the worker container came up.
  @EventListener(ApplicationReadyEvent.class)
  public void announceStart() {
    log.info("Job worker started as {}.", workerId);
  }

  @Scheduled(fixedDelayString = "${vanep.worker.poll-interval}")
  public void runCycle() {
    int recovered = jobs.recoverStuckJobs(lockTimeout, batchSize);
    if (recovered > 0) {
      log.warn("Recovered {} jobs stuck in PROCESSING.", recovered);
    }
    for (JobModel job : jobs.claimDueJobs(workerId, batchSize)) {
      runJobSafely(job);
    }
  }

  // A job whose outcome cannot be saved stays PROCESSING and is recovered after the lock timeout;
  // it must not strand the rest of the batch.
  void runJobSafely(JobModel job) {
    try {
      runJob(job);
    } catch (RuntimeException settleFailure) {
      log.error("Could not settle job {}; left for recovery.", job.getToken(), settleFailure);
    }
  }

  void runJob(JobModel job) {
    JobHandler<?> handler =
        job.findJobType().flatMap(type -> handlers.findHandler(type)).orElse(null);
    if (handler == null) {
      log.warn(
          "Job {} of type {} has no handler; failed permanently.", job.getToken(), job.getType());
      jobs.failPermanently(
          job.getId(),
          new PermanentJobFailureException("No handler registered for job type " + job.getType()));
      return;
    }
    try {
      perform(handler, job);
      jobs.completeJob(job.getId());
    } catch (PermanentJobFailureException permanent) {
      log.warn("Job {} failed permanently.", job.getToken(), permanent);
      jobs.failPermanently(job.getId(), permanent);
    } catch (JobRetryException retry) {
      log.warn("Job {} asked for a retry: {}", job.getToken(), retry.getMessage());
      jobs.registerFailedAttempt(job.getId(), retry, retry.replacementPayload().orElse(null));
    } catch (RuntimeException failure) {
      log.warn("Job {} failed.", job.getToken(), failure);
      jobs.registerFailedAttempt(job.getId(), failure, null);
    }
  }

  <T> void perform(JobHandler<T> handler, JobModel job) {
    T payload = jobs.readPayload(job, handler.payloadType());
    transaction.executeWithoutResult(status -> handler.perform(payload));
  }
}
