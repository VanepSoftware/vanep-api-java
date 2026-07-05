package br.com.vanep.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.auth.web.SignupForm;
import br.com.vanep.user.AuthProvider;
import br.com.vanep.user.OAuthAccountRepository;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.UserType;
import br.com.vanep.user.model.OAuthAccountModel;
import br.com.vanep.user.model.UserModel;
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
    UserModel user = new UserModel();
    user.setId(1L);
    user.setEmail("a@vanep.com");
    OAuthAccountModel account = new OAuthAccountModel();
    account.setUser(user);
    when(oauthAccounts.findByProviderAndProviderUid(AuthProvider.GOOGLE, "sub-1"))
        .thenReturn(Optional.of(account));
    when(users.findById(1L)).thenReturn(Optional.of(user));

    OAuthResolution result =
        service.resolve(AuthProvider.GOOGLE, "sub-1", "a@vanep.com", true, "A");

    assertThat(result.registered()).isTrue();
    assertThat(result.user()).isSameAs(user);
    verify(oauthAccounts, never()).save(any());
  }

  @Test
  void resolveThrowsWhenLinkedAccountIsSoftDeleted() {
    UserModel deleted = new UserModel();
    deleted.setId(99L);
    deleted.setEmail("gone@vanep.com");
    OAuthAccountModel account = new OAuthAccountModel();
    account.setUser(deleted);
    when(oauthAccounts.findByProviderAndProviderUid(AuthProvider.GOOGLE, "sub-x"))
        .thenReturn(Optional.of(account));
    // @SoftDelete: usuário desativado não é retornado por findById.
    when(users.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.resolve(AuthProvider.GOOGLE, "sub-x", "gone@vanep.com", true, "G"))
        .isInstanceOf(OAuth2AuthenticationException.class);
  }

  @Test
  void resolveLinksAccountWhenVerifiedEmailUserExists() {
    when(oauthAccounts.findByProviderAndProviderUid(AuthProvider.GOOGLE, "sub-2"))
        .thenReturn(Optional.empty());
    UserModel existing = new UserModel();
    existing.setEmail("b@vanep.com");
    when(users.findByEmail("b@vanep.com")).thenReturn(Optional.of(existing));
    when(oauthAccounts.save(any(OAuthAccountModel.class))).thenAnswer(inv -> inv.getArgument(0));

    OAuthResolution result =
        service.resolve(AuthProvider.GOOGLE, "sub-2", "b@vanep.com", true, "B");

    assertThat(result.registered()).isTrue();
    assertThat(result.user()).isSameAs(existing);
    verify(oauthAccounts).save(any(OAuthAccountModel.class));
  }

  @Test
  void resolveDoesNotLinkWhenEmailNotVerified() {
    when(oauthAccounts.findByProviderAndProviderUid(AuthProvider.GOOGLE, "sub-5"))
        .thenReturn(Optional.empty());

    OAuthResolution result =
        service.resolve(AuthProvider.GOOGLE, "sub-5", "b@vanep.com", false, "B");

    assertThat(result.registered()).isFalse();
    verify(users, never()).findByEmail(any());
    verify(oauthAccounts, never()).save(any());
  }

  @Test
  void resolveReturnsPendingWhenNothingMatches() {
    when(oauthAccounts.findByProviderAndProviderUid(AuthProvider.GOOGLE, "sub-3"))
        .thenReturn(Optional.empty());
    when(users.findByEmail("c@vanep.com")).thenReturn(Optional.empty());

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
    when(users.save(any(UserModel.class))).thenAnswer(inv -> inv.getArgument(0));
    when(oauthAccounts.save(any(OAuthAccountModel.class))).thenAnswer(inv -> inv.getArgument(0));

    SignupForm form = new SignupForm();
    form.setType(UserType.DRIVER);
    form.setDocument("99999999999");
    form.setPhone("11999998888");
    form.setAcceptTerms(true);

    UserModel created =
        service.completeRegistration(AuthProvider.GOOGLE, "sub-4", "d@vanep.com", "Driver D", form);

    assertThat(created.getType()).isEqualTo(UserType.DRIVER);
    assertThat(created.getEmail()).isEqualTo("d@vanep.com");
    assertThat(created.getName()).isEqualTo("Driver D");
    assertThat(created.getDocument()).isEqualTo("99999999999");
    assertThat(created.isVerified()).isTrue();
    assertThat(created.getTermsAcceptedAt()).isNotNull();
    verify(users).save(any(UserModel.class));
    verify(oauthAccounts).save(any(OAuthAccountModel.class));
  }
}
