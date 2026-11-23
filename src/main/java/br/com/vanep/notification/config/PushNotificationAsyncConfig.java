package br.com.vanep.notification.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class PushNotificationAsyncConfig {

  public static final String EXECUTOR = "pushNotificationExecutor";

  private static final Logger log = LoggerFactory.getLogger(PushNotificationAsyncConfig.class);

  // Not a default candidate, so it never replaces the executors other beans autowire by type.
  // A full queue drops the push instead of throwing: the rejection would surface in the
  // after-commit callback and fail a request whose transaction already committed.
  @Bean(name = EXECUTOR, defaultCandidate = false)
  public ThreadPoolTaskExecutor pushNotificationExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(1000);
    executor.setThreadNamePrefix("push-");
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(10);
    executor.setRejectedExecutionHandler(
        (task, pool) -> log.warn("Push notification queue is full; dropping a notification."));
    return executor;
  }
}
