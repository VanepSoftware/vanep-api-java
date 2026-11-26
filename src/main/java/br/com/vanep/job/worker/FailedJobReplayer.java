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
  private final int maxTries;

  public FailedJobReplayer(
      FailedJobService failedJobs,
      @Value("${vanep.worker.replay.batch-size}") int batchSize,
      @Value("${vanep.worker.replay.max-tries}") int maxTries) {
    this.failedJobs = failedJobs;
    this.batchSize = batchSize;
    this.maxTries = maxTries;
  }

  @Scheduled(cron = "${vanep.worker.replay.cron}", zone = "America/Sao_Paulo")
  public void replayFailedJobs() {
    int replayed = failedJobs.replayFailedJobs(batchSize, maxTries);
    if (replayed > 0) {
      log.info("Sent {} failed jobs back to the queue.", replayed);
    }
  }
}
