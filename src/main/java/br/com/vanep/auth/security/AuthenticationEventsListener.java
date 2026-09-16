package br.com.vanep.auth.security;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationEventsListener {

  private final LoginAttemptService loginAttempts;
  private final LoginActivityService loginActivity;

  public AuthenticationEventsListener(
      LoginAttemptService loginAttempts, LoginActivityService loginActivity) {
    this.loginAttempts = loginAttempts;
    this.loginActivity = loginActivity;
  }

  @EventListener
  public void onBadCredentials(AuthenticationFailureBadCredentialsEvent event) {
    loginAttempts.loginFailed(event.getAuthentication().getName());
  }

  @EventListener
  public void onInteractiveSuccess(InteractiveAuthenticationSuccessEvent event) {
    loginActivity.recordSuccessfulLogin(event.getAuthentication().getName());
  }
}
