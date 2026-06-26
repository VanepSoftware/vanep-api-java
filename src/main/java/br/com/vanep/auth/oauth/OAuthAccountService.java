package br.com.vanep.auth.oauth;

import br.com.vanep.auth.web.SignupForm;
import br.com.vanep.user.AuthProvider;
import br.com.vanep.user.OAuthAccount;
import br.com.vanep.user.OAuthAccountRepository;
import br.com.vanep.user.User;
import br.com.vanep.user.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolve logins sociais: encontra/vincula uma conta ou sinaliza cadastro pendente. */
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
      AuthProvider provider, String providerUid, String email, String name) {
    Optional<OAuthAccount> existing =
        oauthAccounts.findByProviderAndProviderUid(provider, providerUid);
    if (existing.isPresent()) {
      return OAuthResolution.registered(existing.get().getUser());
    }

    if (email != null && !email.isBlank()) {
      Optional<User> byEmail = users.findByEmailAndDeletedAtIsNull(email);
      if (byEmail.isPresent()) {
        // Conta local já existe com este e-mail (verificado pelo provedor) → vincula.
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
    user.setVerified(true); // e-mail já verificado pelo provedor social
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
