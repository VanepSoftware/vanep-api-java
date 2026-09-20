package br.com.vanep.auth.oauth.grant;

import br.com.vanep.auth.security.LoginActivityService;
import br.com.vanep.auth.security.LoginAttemptService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

/**
 * Checks local credentials for the native app. The account state is reported the way the spec
 * requires: an unknown e-mail, a wrong password and a Google-only account are indistinguishable,
 * {@code email_not_verified} only appears once the password is right, and a locked e-mail answers
 * the same whether or not it exists.
 */
public final class MobilePasswordGrantAuthenticationProvider implements AuthenticationProvider {

  private final DaoAuthenticationProvider credentials;
  private final MobileGrantTokenIssuer tokenIssuer;
  private final LoginAttemptService loginAttempts;
  private final LoginActivityService loginActivity;
  private final MessageSource messages;

  public MobilePasswordGrantAuthenticationProvider(
      UserDetailsService userDetailsService,
      PasswordEncoder passwordEncoder,
      MobileGrantTokenIssuer tokenIssuer,
      LoginAttemptService loginAttempts,
      LoginActivityService loginActivity,
      MessageSource messages) {
    this.credentials = dedicatedCredentialsProvider(userDetailsService, passwordEncoder);
    this.tokenIssuer = tokenIssuer;
    this.loginAttempts = loginAttempts;
    this.loginActivity = loginActivity;
    this.messages = messages;
  }

  /**
   * Its own instance, called outside the {@code ProviderManager}: the form login listener would
   * otherwise count the same failure twice.
   */
  private static DaoAuthenticationProvider dedicatedCredentialsProvider(
      UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
    provider.setPasswordEncoder(passwordEncoder);
    provider.setPreAuthenticationChecks(
        user -> {
          if (!user.isAccountNonLocked()) {
            throw new LockedException("Account locked");
          }
          if (!user.isAccountNonExpired()) {
            throw new AccountExpiredException("Account expired");
          }
        });
    provider.setPostAuthenticationChecks(
        user -> {
          if (!user.isCredentialsNonExpired()) {
            throw new CredentialsExpiredException("Credentials expired");
          }
          if (!user.isEnabled()) {
            throw new DisabledException("Email not verified");
          }
        });
    return provider;
  }

  @Override
  public Authentication authenticate(Authentication authentication) throws AuthenticationException {
    MobilePasswordGrantAuthenticationToken request =
        (MobilePasswordGrantAuthenticationToken) authentication;
    OAuth2ClientAuthenticationToken clientPrincipal = authenticatedClient(request);
    RegisteredClient registeredClient = clientPrincipal.getRegisteredClient();
    if (registeredClient == null
        || !registeredClient
            .getAuthorizationGrantTypes()
            .contains(MobileAuthorizationGrantTypes.PASSWORD)) {
      throw new OAuth2AuthenticationException(
          new OAuth2Error(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT));
    }

    Authentication userPrincipal = authenticateCredentials(request);
    loginActivity.recordSuccessfulLogin(request.getUsername());

    return tokenIssuer.issue(
        clientPrincipal, userPrincipal, MobileAuthorizationGrantTypes.PASSWORD, request);
  }

  private Authentication authenticateCredentials(MobilePasswordGrantAuthenticationToken request) {
    // Checked here as well because DaoAuthenticationProvider wraps anything the user details
    // service throws (other than "not found") into InternalAuthenticationServiceException, which
    // would flatten a locked e-mail into invalid_grant.
    if (loginAttempts.isBlocked(request.getUsername())) {
      throw grantError("account_locked", "auth.grant.account_locked");
    }
    try {
      return credentials.authenticate(
          UsernamePasswordAuthenticationToken.unauthenticated(
              request.getUsername(), request.getPassword()));
    } catch (LockedException ex) {
      throw grantError("account_locked", "auth.grant.account_locked");
    } catch (DisabledException ex) {
      throw grantError("email_not_verified", "auth.grant.email_not_verified");
    } catch (AuthenticationException ex) {
      loginAttempts.loginFailed(request.getUsername());
      throw grantError(OAuth2ErrorCodes.INVALID_GRANT, "auth.grant.invalid_credentials");
    }
  }

  private static OAuth2ClientAuthenticationToken authenticatedClient(
      Authentication authentication) {
    if (authentication.getPrincipal() instanceof OAuth2ClientAuthenticationToken client
        && client.isAuthenticated()
        && client.getRegisteredClient() != null) {
      return client;
    }
    throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_CLIENT));
  }

  private OAuth2AuthenticationException grantError(String errorCode, String messageKey) {
    return new OAuth2AuthenticationException(
        new OAuth2Error(
            errorCode,
            messages.getMessage(messageKey, null, LocaleContextHolder.getLocale()),
            null));
  }

  @Override
  public boolean supports(Class<?> authentication) {
    return MobilePasswordGrantAuthenticationToken.class.isAssignableFrom(authentication);
  }
}
