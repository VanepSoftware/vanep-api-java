package br.com.vanep.seed;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "vanep.seed.only", havingValue = "true")
public class SeedOnlyExitConfiguration {

  @Bean
  ApplicationListener<ApplicationReadyEvent> seedOnlyExitListener() {
    return event -> {
      int exitCode = SpringApplication.exit(event.getApplicationContext());
      System.exit(exitCode);
    };
  }
}
