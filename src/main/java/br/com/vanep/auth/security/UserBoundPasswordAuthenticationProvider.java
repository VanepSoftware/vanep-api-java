package br.com.vanep.auth.security;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Checks the password with the account token. {@link PasswordEncoder#matches} cannot do that: it
 * never receives the user, so a hash copied from another row would still match.
 */
public final class UserBoundPasswordAuthenticationProvider extends DaoAuthenticationProvider {

  private final UserPasswordEncoder userPasswords;
  private final UserPasswordRehashService rehash;

  public UserBoundPasswordAuthenticationProvider(
      UserDetailsService userDetailsService,
      UserPasswordEncoder userPasswords,
      UserPasswordRehashService rehash) {
    super(userDetailsService);
    this.userPasswords = userPasswords;
    this.rehash = rehash;
  }

  public static UserBoundPasswordAuthenticationProvider create(
      UserDetailsService userDetailsService,
      PasswordEncoder passwordEncoder,
      UserPasswordEncoder userPasswords,
      UserPasswordRehashService rehash) {
    UserBoundPasswordAuthenticationProvider provider =
        new UserBoundPasswordAuthenticationProvider(userDetailsService, userPasswords, rehash);
    provider.setPasswordEncoder(passwordEncoder);
    provider.afterPropertiesSet();
    return provider;
  }

  @Override
  protected void additionalAuthenticationChecks(
      UserDetails userDetails, UsernamePasswordAuthenticationToken authentication)
      throws AuthenticationException {
    if (authentication.getCredentials() == null) {
      this.logger.debug("Failed to authenticate since no credentials provided");
      throw new BadCredentialsException(
          this.messages.getMessage(
              "AbstractUserDetailsAuthenticationProvider.badCredentials", "Bad credentials"));
    }
    String presentedPassword = authentication.getCredentials().toString();
    if (!this.userPasswords.matches(
        presentedPassword, userDetails.getPassword(), userTokenOf(userDetails))) {
      this.logger.debug("Failed to authenticate since password does not match stored value");
      throw new BadCredentialsException(
          this.messages.getMessage(
              "AbstractUserDetailsAuthenticationProvider.badCredentials", "Bad credentials"));
    }
  }

  @Override
  protected Authentication createSuccessAuthentication(
      Object principal, Authentication authentication, UserDetails user) {
    UserDetails current = upgradeLegacyHash(authentication, user);
    Object principalToReturn = principal == user ? current : principal;
    return super.createSuccessAuthentication(principalToReturn, authentication, current);
  }

  private UserDetails upgradeLegacyHash(Authentication authentication, UserDetails user) {
    if (!this.userPasswords.requiresUpgrade(user.getPassword())) {
      return user;
    }
    if (!(user instanceof VanepUserDetails details)) {
      return user;
    }
    String userToken = details.getUserToken();
    if (userToken == null || userToken.isBlank()) {
      return user;
    }
    String upgraded =
        this.userPasswords.encode(authentication.getCredentials().toString(), userToken);
    return this.rehash.storeUpgradedHash(details, upgraded);
  }

  private static String userTokenOf(UserDetails userDetails) {
    if (userDetails instanceof VanepUserDetails details) {
      return details.getUserToken();
    }
    return null;
  }
}
