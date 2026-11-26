package br.com.vanep.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

@Configuration
@EnableScheduling
public class SchedulingConfig implements SchedulingConfigurer {

  static final int POOL_SIZE = 2;

  // Without its own scheduler, @Scheduled takes the STOMP broker's, the only TaskScheduler in the
  // context, and shares its threads with the chat heartbeats.
  //
  // Graceful shutdown comes from the context lifecycle: stopping the scheduler waits for the
  // running job batch (up to spring.lifecycle.timeout-per-shutdown-phase). Turning on
  // waitForTasksToCompleteOnShutdown as well would await threads parked by that stop and hold
  // every shutdown for the whole timeout.
  @Bean
  public ThreadPoolTaskScheduler schedulingTaskScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(POOL_SIZE);
    scheduler.setThreadNamePrefix("scheduling-");
    return scheduler;
  }

  @Override
  public void configureTasks(ScheduledTaskRegistrar registrar) {
    registrar.setTaskScheduler(schedulingTaskScheduler());
  }
}
