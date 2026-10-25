package br.com.vanep.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "vanep.expiry-sweep.enabled", havingValue = "true")
public class SchedulingConfig implements SchedulingConfigurer {

  // Without its own scheduler, @Scheduled takes the STOMP broker's, the only TaskScheduler in the
  // context, and shares its threads with the chat heartbeats.
  @Bean
  public ThreadPoolTaskScheduler schedulingTaskScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("scheduling-");
    return scheduler;
  }

  @Override
  public void configureTasks(ScheduledTaskRegistrar registrar) {
    registrar.setTaskScheduler(schedulingTaskScheduler());
  }
}
