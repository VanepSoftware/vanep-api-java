package br.com.vanep.auth.security;

import br.com.vanep.user.UserRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AuthenticationEventsListener {

  private static final Logger log = LoggerFactory.getLogger(AuthenticationEventsListener.class);

  private final LoginAttemptService loginAttempts;
  private final UserRepository users;

  public AuthenticationEventsListener(LoginAttemptService loginAttempts, UserRepository users) {
    this.loginAttempts = loginAttempts;
    this.users = users;
  }

  @EventListener
  public void onBadCredentials(AuthenticationFailureBadCredentialsEvent event) {
    loginAttempts.loginFailed(event.getAuthentication().getName());
  }

  @EventListener
  @Transactional
  public void onInteractiveSuccess(InteractiveAuthenticationSuccessEvent event) {
    String email = event.getAuthentication().getName();
    loginAttempts.loginSucceeded(email);
    try {
      users.findByEmail(email).ifPresent(user -> user.setLastLoginAt(Instant.now()));
    } catch (RuntimeException ex) {
      log.warn("Falha ao atualizar last_login_at de {}.", email, ex);
    }
  }
}
