package br.com.vanep.job.worker;

import br.com.vanep.job.service.FailedJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "vanep.worker.enabled", havingValue = "true")
public class FailedJobReplayer {

  private static final Logger log = LoggerFactory.getLogger(FailedJobReplayer.class);

  private final FailedJobService failedJobs;
  private final int batchSize;

  public FailedJobReplayer(
      FailedJobService failedJobs, @Value("${vanep.worker.replay.batch-size}") int batchSize) {
    this.failedJobs = failedJobs;
    this.batchSize = batchSize;
  }

  // Each row says when it is due (failed_jobs.retry_at); this only checks often enough to honour
  // the shortest delay.
  @Scheduled(fixedDelayString = "${vanep.worker.replay.poll-interval}")
  public void replayFailedJobs() {
    int replayed = failedJobs.replayFailedJobs(batchSize);
    if (replayed > 0) {
      log.info("Sent {} failed jobs back to the queue.", replayed);
    }
  }
}
