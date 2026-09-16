package br.com.vanep.auth.security;

import br.com.vanep.user.repository.UserRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Shared by the web form login listener and the mobile password grant. */
@Service
public class LoginActivityService {

  private static final Logger log = LoggerFactory.getLogger(LoginActivityService.class);

  private final LoginAttemptService loginAttempts;
  private final UserRepository users;

  public LoginActivityService(LoginAttemptService loginAttempts, UserRepository users) {
    this.loginAttempts = loginAttempts;
    this.users = users;
  }

  @Transactional
  public void recordSuccessfulLogin(String email) {
    loginAttempts.loginSucceeded(email);
    try {
      users.findByEmail(email).ifPresent(user -> user.setLastLoginAt(Instant.now()));
    } catch (RuntimeException ex) {
      log.warn("Could not update last_login_at for {}.", email, ex);
    }
  }
}
