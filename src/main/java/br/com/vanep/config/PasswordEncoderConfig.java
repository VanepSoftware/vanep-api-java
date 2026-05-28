package br.com.vanep.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordEncoderConfig {

  @Bean
  public PasswordEncoder passwordEncoder(@Value("${VANEP_PASSWORD_PEPPER}") String pepper) {
    PasswordEncoder argon2 = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    return new PepperedPasswordEncoder(argon2, pepper);
  }
}
