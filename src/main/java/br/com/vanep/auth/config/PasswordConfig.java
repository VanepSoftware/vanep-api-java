package br.com.vanep.auth.config;

import br.com.vanep.auth.security.PepperedArgon2PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordConfig {

  @Bean
  public PasswordEncoder passwordEncoder(@Value("${vanep.password.pepper}") String pepper) {
    return new PepperedArgon2PasswordEncoder(pepper);
  }
}
