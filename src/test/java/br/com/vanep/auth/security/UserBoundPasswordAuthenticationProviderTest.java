package br.com.vanep.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

class UserBoundPasswordAuthenticationProviderTest {

  private static final String PASSWORD = "s3cr3t!";
  private static final String TOKEN_A = "user-token-alpha";
  private static final String TOKEN_B = "user-token-beta";
  private static final String EMAIL = "person@vanep.com";
  private static final PepperedArgon2PasswordEncoder ENCODER =
      new PepperedArgon2PasswordEncoder("unit-test-pepper");
  private static final String LEGACY = ENCODER.encode(PASSWORD);
  private static final String BOUND_A = ENCODER.encode(PASSWORD, TOKEN_A);

  @Test
  void acceptsThePasswordBoundToThatUser() {
    UserModel model = account(BOUND_A, TOKEN_A);
    UserRepository users = mock(UserRepository.class);
    Authentication result =
        provider(users, details(vanep(BOUND_A, TOKEN_A)))
            .authenticate(UsernamePasswordAuthenticationToken.unauthenticated(EMAIL, PASSWORD));

    assertThat(result.isAuthenticated()).isTrue();
    assertThat(model.getPassword()).isEqualTo(BOUND_A);
    verify(users, never()).findByEmail(EMAIL);
  }

  @Test
  void rejectsAWrongPasswordWithoutUpgrading() {
    UserModel model = account(BOUND_A, TOKEN_A);
    UserRepository users = mock(UserRepository.class);

    assertThatThrownBy(
            () ->
                provider(users, details(vanep(BOUND_A, TOKEN_A)))
                    .authenticate(
                        UsernamePasswordAuthenticationToken.unauthenticated(EMAIL, "wrong")))
        .isInstanceOf(BadCredentialsException.class);

    assertThat(model.getPassword()).isEqualTo(BOUND_A);
    verify(users, never()).findByEmail(EMAIL);
  }

  @Test
  void rejectsAHashBoundToAnotherUser() {
    UserRepository users = mock(UserRepository.class);

    assertThatThrownBy(
            () ->
                provider(users, details(vanep(BOUND_A, TOKEN_B)))
                    .authenticate(
                        UsernamePasswordAuthenticationToken.unauthenticated(EMAIL, PASSWORD)))
        .isInstanceOf(BadCredentialsException.class);

    verify(users, never()).findByEmail(EMAIL);
  }

  @Test
  void upgradesALegacyHashAfterASuccessfulLogin() {
    UserModel model = account(LEGACY, TOKEN_A);
    UserRepository users = repositoryReturning(model);

    Authentication result =
        provider(users, details(vanep(LEGACY, TOKEN_A)))
            .authenticate(UsernamePasswordAuthenticationToken.unauthenticated(EMAIL, PASSWORD));

    assertThat(result.getPrincipal()).isInstanceOf(VanepUserDetails.class);
    String upgraded = ((VanepUserDetails) result.getPrincipal()).getPassword();
    assertThat(ENCODER.requiresUpgrade(upgraded)).isFalse();
    assertThat(upgraded).doesNotContain(TOKEN_A);
    assertThat(ENCODER.matches(PASSWORD, upgraded, TOKEN_A)).isTrue();
    assertThat(ENCODER.matches(PASSWORD, upgraded, TOKEN_B)).isFalse();
    assertThat(ENCODER.matches(PASSWORD, upgraded)).isFalse();
    assertThat(model.getPassword()).isEqualTo(upgraded);
  }

  @Test
  void doesNotUpgradeWhenTheLoadedAccountIsNotBoundToAToken() {
    UserModel model = account(LEGACY, TOKEN_A);
    UserRepository users = mock(UserRepository.class);
    UserDetails plain =
        User.withUsername(EMAIL).password(LEGACY).authorities("ROLE_CLIENT").build();

    Authentication result =
        provider(users, details(plain))
            .authenticate(UsernamePasswordAuthenticationToken.unauthenticated(EMAIL, PASSWORD));

    assertThat(result.isAuthenticated()).isTrue();
    assertThat(model.getPassword()).isEqualTo(LEGACY);
    verify(users, never()).findByEmail(EMAIL);
  }

  @Test
  void doesNotUpgradeALegacyHashWhenTheUserTokenIsBlank() {
    UserModel model = account(LEGACY, TOKEN_A);
    UserRepository users = mock(UserRepository.class);

    Authentication result =
        provider(users, details(vanep(LEGACY, " ")))
            .authenticate(UsernamePasswordAuthenticationToken.unauthenticated(EMAIL, PASSWORD));

    assertThat(result.isAuthenticated()).isTrue();
    assertThat(model.getPassword()).isEqualTo(LEGACY);
    verify(users, never()).findByEmail(EMAIL);
  }

  @Test
  void rejectsMissingCredentials() {
    UserRepository users = mock(UserRepository.class);

    assertThatThrownBy(
            () ->
                provider(users, details(vanep(BOUND_A, TOKEN_A)))
                    .authenticate(UsernamePasswordAuthenticationToken.unauthenticated(EMAIL, null)))
        .isInstanceOf(BadCredentialsException.class);
  }

  @Test
  void doesNotReplaceAHashAlreadyUpgradedInTheDatabase() {
    UserModel model = account(BOUND_A, TOKEN_A);
    UserRepository users = repositoryReturning(model);
    UserPasswordRehashService rehash = new UserPasswordRehashService(users, ENCODER);

    UserDetails result = rehash.storeUpgradedHash(vanep(LEGACY, TOKEN_A), "replacement");

    assertThat(model.getPassword()).isEqualTo(BOUND_A);
    assertThat(result.getPassword()).isEqualTo(LEGACY);
  }

  @Test
  void leavesTheHashUntouchedWhenTheAccountDisappearsBeforeUpgrade() {
    UserRepository users = mock(UserRepository.class);
    when(users.findByEmail(EMAIL)).thenReturn(Optional.empty());
    UserPasswordRehashService rehash = new UserPasswordRehashService(users, ENCODER);
    VanepUserDetails user = vanep(LEGACY, TOKEN_A);

    assertThat(rehash.storeUpgradedHash(user, "replacement")).isSameAs(user);
  }

  private static UserBoundPasswordAuthenticationProvider provider(
      UserRepository users, UserDetailsService details) {
    return UserBoundPasswordAuthenticationProvider.create(
        details, ENCODER, ENCODER, new UserPasswordRehashService(users, ENCODER));
  }

  private static UserDetailsService details(UserDetails user) {
    UserDetailsService service = mock(UserDetailsService.class);
    when(service.loadUserByUsername(EMAIL)).thenReturn(user);
    return service;
  }

  private static UserRepository repositoryReturning(UserModel model) {
    UserRepository users = mock(UserRepository.class);
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(model));
    return users;
  }

  private static UserModel account(String password, String token) {
    UserModel model = new UserModel();
    model.setEmail(EMAIL);
    model.setToken(token);
    model.setPassword(password);
    return model;
  }

  private static VanepUserDetails vanep(String password, String token) {
    return new VanepUserDetails(
        EMAIL, password, token, true, true, List.of(new SimpleGrantedAuthority("ROLE_CLIENT")));
  }
}
