package br.com.vanep.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.auth.web.SignupForm;
import br.com.vanep.user.AuthProvider;
import br.com.vanep.user.OAuthAccount;
import br.com.vanep.user.OAuthAccountRepository;
import br.com.vanep.user.User;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.UserType;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

@ExtendWith(MockitoExtension.class)
class OAuthAccountServiceTest {

  @Mock private UserRepository users;
  @Mock private OAuthAccountRepository oauthAccounts;
  @InjectMocks private OAuthAccountService service;

  @Test
  void resolveReturnsRegisteredWhenAccountExists() {
    User user = new User();
    user.setEmail("a@vanep.com");
    OAuthAccount account = new OAuthAccount();
    account.setUser(user);
    when(oauthAccounts.findByProviderAndProviderUid(AuthProvider.GOOGLE, "sub-1"))
        .thenReturn(Optional.of(account));

    OAuthResolution result =
        service.resolve(AuthProvider.GOOGLE, "sub-1", "a@vanep.com", true, "A");

    assertThat(result.registered()).isTrue();
    assertThat(result.user()).isSameAs(user);
    verify(oauthAccounts, never()).save(any());
  }

  @Test
  void resolveThrowsWhenLinkedAccountIsSoftDeleted() {
    User deleted = new User();
    deleted.setEmail("gone@vanep.com");
    deleted.setDeletedAt(Instant.now());
    OAuthAccount account = new OAuthAccount();
    account.setUser(deleted);
    when(oauthAccounts.findByProviderAndProviderUid(AuthProvider.GOOGLE, "sub-x"))
        .thenReturn(Optional.of(account));

    assertThatThrownBy(
            () -> service.resolve(AuthProvider.GOOGLE, "sub-x", "gone@vanep.com", true, "G"))
        .isInstanceOf(OAuth2AuthenticationException.class);
  }

  @Test
  void resolveLinksAccountWhenVerifiedEmailUserExists() {
    when(oauthAccounts.findByProviderAndProviderUid(AuthProvider.GOOGLE, "sub-2"))
        .thenReturn(Optional.empty());
    User existing = new User();
    existing.setEmail("b@vanep.com");
    when(users.findByEmailAndDeletedAtIsNull("b@vanep.com")).thenReturn(Optional.of(existing));
    when(oauthAccounts.save(any(OAuthAccount.class))).thenAnswer(inv -> inv.getArgument(0));

    OAuthResolution result =
        service.resolve(AuthProvider.GOOGLE, "sub-2", "b@vanep.com", true, "B");

    assertThat(result.registered()).isTrue();
    assertThat(result.user()).isSameAs(existing);
    verify(oauthAccounts).save(any(OAuthAccount.class));
  }

  @Test
  void resolveDoesNotLinkWhenEmailNotVerified() {
    when(oauthAccounts.findByProviderAndProviderUid(AuthProvider.GOOGLE, "sub-5"))
        .thenReturn(Optional.empty());

    OAuthResolution result =
        service.resolve(AuthProvider.GOOGLE, "sub-5", "b@vanep.com", false, "B");

    assertThat(result.registered()).isFalse();
    verify(users, never()).findByEmailAndDeletedAtIsNull(any());
    verify(oauthAccounts, never()).save(any());
  }

  @Test
  void resolveReturnsPendingWhenNothingMatches() {
    when(oauthAccounts.findByProviderAndProviderUid(AuthProvider.GOOGLE, "sub-3"))
        .thenReturn(Optional.empty());
    when(users.findByEmailAndDeletedAtIsNull("c@vanep.com")).thenReturn(Optional.empty());

    OAuthResolution result =
        service.resolve(AuthProvider.GOOGLE, "sub-3", "c@vanep.com", true, "C");

    assertThat(result.registered()).isFalse();
    assertThat(result.provider()).isEqualTo(AuthProvider.GOOGLE);
    assertThat(result.providerUid()).isEqualTo("sub-3");
    assertThat(result.email()).isEqualTo("c@vanep.com");
    verify(users, never()).save(any());
  }

  @Test
  void completeRegistrationCreatesUserAndLinksAccount() {
    when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    when(oauthAccounts.save(any(OAuthAccount.class))).thenAnswer(inv -> inv.getArgument(0));

    SignupForm form = new SignupForm();
    form.setType(UserType.DRIVER);
    form.setDocument("99999999999");
    form.setPhone("11999998888");
    form.setAcceptTerms(true);

    User created =
        service.completeRegistration(AuthProvider.GOOGLE, "sub-4", "d@vanep.com", "Driver D", form);

    assertThat(created.getType()).isEqualTo(UserType.DRIVER);
    assertThat(created.getEmail()).isEqualTo("d@vanep.com");
    assertThat(created.getName()).isEqualTo("Driver D");
    assertThat(created.getDocument()).isEqualTo("99999999999");
    assertThat(created.isVerified()).isTrue();
    assertThat(created.getTermsAcceptedAt()).isNotNull();
    verify(users).save(any(User.class));
    verify(oauthAccounts).save(any(OAuthAccount.class));
  }
}
