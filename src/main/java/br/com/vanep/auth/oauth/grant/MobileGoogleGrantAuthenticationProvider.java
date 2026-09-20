package br.com.vanep.auth.oauth.grant;

import br.com.vanep.auth.oauth.GoogleIdTokenValidator;
import br.com.vanep.auth.oauth.GoogleIdentity;
import br.com.vanep.auth.oauth.OAuthAccountService;
import br.com.vanep.auth.oauth.OAuthResolution;
import br.com.vanep.auth.signup.SignupTicketService;
import br.com.vanep.user.enums.AuthProvider;
import br.com.vanep.user.model.UserModel;
import java.util.List;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

/**
 * Exchanges the ID token the native Google SDK produced for Vanep tokens, or for a single-use
 * sign-up ticket when the person has no account yet.
 */
public final class MobileGoogleGrantAuthenticationProvider implements AuthenticationProvider {

  private final GoogleIdTokenValidator idTokenValidator;
  private final OAuthAccountService accounts;
  private final SignupTicketService signupTickets;
  private final MobileGrantTokenIssuer tokenIssuer;
  private final MessageSource messages;

  public MobileGoogleGrantAuthenticationProvider(
      GoogleIdTokenValidator idTokenValidator,
      OAuthAccountService accounts,
      SignupTicketService signupTickets,
      MobileGrantTokenIssuer tokenIssuer,
      MessageSource messages) {
    this.idTokenValidator = idTokenValidator;
    this.accounts = accounts;
    this.signupTickets = signupTickets;
    this.tokenIssuer = tokenIssuer;
    this.messages = messages;
  }

  @Override
  public Authentication authenticate(Authentication authentication) throws AuthenticationException {
    MobileGoogleGrantAuthenticationToken request =
        (MobileGoogleGrantAuthenticationToken) authentication;
    OAuth2ClientAuthenticationToken clientPrincipal = authenticatedClient(request);
    RegisteredClient registeredClient = clientPrincipal.getRegisteredClient();
    if (registeredClient == null
        || !registeredClient
            .getAuthorizationGrantTypes()
            .contains(MobileAuthorizationGrantTypes.GOOGLE)) {
      throw new OAuth2AuthenticationException(
          new OAuth2Error(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT));
    }

    GoogleIdentity identity = idTokenValidator.validate(request.getIdToken());
    OAuthResolution resolution =
        accounts.resolve(
            AuthProvider.GOOGLE, identity.subject(), identity.email(), true, identity.name());

    if (!resolution.registered()) {
      throw registrationRequired(resolution);
    }

    return tokenIssuer.issue(
        clientPrincipal,
        userPrincipalOf(resolution.user()),
        MobileAuthorizationGrantTypes.GOOGLE,
        request);
  }

  private RegistrationRequiredException registrationRequired(OAuthResolution resolution) {
    String ticket =
        signupTickets.issue(
            resolution.provider(), resolution.providerUid(), resolution.email(), resolution.name());
    return new RegistrationRequiredException(
        messages.getMessage(
            "auth.grant.registration_required", null, LocaleContextHolder.getLocale()),
        ticket,
        resolution.email(),
        resolution.name());
  }

  private static Authentication userPrincipalOf(UserModel user) {
    return UsernamePasswordAuthenticationToken.authenticated(
        user.getEmail(),
        null,
        List.of(new SimpleGrantedAuthority("ROLE_" + user.getType().name())));
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

  @Override
  public boolean supports(Class<?> authentication) {
    return MobileGoogleGrantAuthenticationToken.class.isAssignableFrom(authentication);
  }
}
