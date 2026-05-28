package br.com.vanep.config;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class PasswordHasher {

  private final PasswordEncoder passwordEncoder;

  public PasswordHasher(PasswordEncoder passwordEncoder) {
    this.passwordEncoder = passwordEncoder;
  }

  public String encode(String rawPassword) {
    return passwordEncoder.encode(rawPassword);
  }

  public boolean matches(String rawPassword, String encodedPassword) {
    return passwordEncoder.matches(rawPassword, encodedPassword);
  }
}
