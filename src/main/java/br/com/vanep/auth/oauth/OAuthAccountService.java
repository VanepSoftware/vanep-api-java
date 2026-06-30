package br.com.vanep.auth.oauth;

import br.com.vanep.auth.web.SignupForm;
import br.com.vanep.user.AuthProvider;
import br.com.vanep.user.OAuthAccount;
import br.com.vanep.user.OAuthAccountRepository;
import br.com.vanep.user.User;
import br.com.vanep.user.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OAuthAccountService {

  private final UserRepository users;
  private final OAuthAccountRepository oauthAccounts;

  public OAuthAccountService(UserRepository users, OAuthAccountRepository oauthAccounts) {
    this.users = users;
    this.oauthAccounts = oauthAccounts;
  }

  @Transactional
  public OAuthResolution resolve(
      AuthProvider provider, String providerUid, String email, boolean emailVerified, String name) {
    Optional<OAuthAccount> existing =
        oauthAccounts.findByProviderAndProviderUid(provider, providerUid);
    if (existing.isPresent()) {

      // @SoftDelete filtra contas removidas: findById não retorna usuário desativado.
      User user =
          users
              .findById(existing.get().getUser().getId())
              .orElseThrow(
                  () ->
                      new OAuth2AuthenticationException(
                          new OAuth2Error("account_disabled", "Esta conta foi desativada.", null)));
      return OAuthResolution.registered(user);
    }

    if (emailVerified && email != null && !email.isBlank()) {
      Optional<User> byEmail = users.findByEmail(email);
      if (byEmail.isPresent()) {
        OAuthAccount linked = link(byEmail.get(), provider, providerUid, email);
        return OAuthResolution.registered(linked.getUser());
      }
    }

    return OAuthResolution.pending(provider, providerUid, email, name);
  }

  @Transactional
  public User completeRegistration(
      AuthProvider provider, String providerUid, String email, String name, SignupForm form) {
    User user = new User();
    user.setType(form.getType());
    user.setName(name != null && !name.isBlank() ? name : form.getName());
    user.setEmail(email);
    user.setDocument(form.getDocument());
    user.setPhone(form.getPhone());
    user.setBirthDate(form.getBirthDate());
    user.setGender(form.getGender());
    user.setVerified(true);
    user.setTermsAcceptedAt(Instant.now());
    users.save(user);

    link(user, provider, providerUid, email);
    return user;
  }

  private OAuthAccount link(User user, AuthProvider provider, String providerUid, String email) {
    OAuthAccount account = new OAuthAccount();
    account.setUser(user);
    account.setProvider(provider);
    account.setProviderUid(providerUid);
    account.setEmail(email);
    return oauthAccounts.save(account);
  }
}
