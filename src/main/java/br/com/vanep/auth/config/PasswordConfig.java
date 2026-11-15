package br.com.vanep.auth.config;

import br.com.vanep.auth.security.PepperedArgon2PasswordEncoder;
import br.com.vanep.auth.security.UserBoundPasswordEncoder;
import br.com.vanep.auth.security.UserPasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PasswordConfig {

  @Bean
  public PepperedArgon2PasswordEncoder passwordEncoder(
      @Value("${vanep.password.pepper}") String pepper) {
    return new PepperedArgon2PasswordEncoder(pepper);
  }

  @Bean
  public UserPasswordEncoder userPasswordEncoder(PepperedArgon2PasswordEncoder passwordEncoder) {
    return new UserBoundPasswordEncoder(passwordEncoder);
  }
}
