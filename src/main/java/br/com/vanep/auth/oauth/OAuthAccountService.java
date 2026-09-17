package br.com.vanep.auth.oauth;

import br.com.vanep.auth.dto.SignupCompletionFields;
import br.com.vanep.auth.web.RegistrationService;
import br.com.vanep.role.RoleName;
import br.com.vanep.role.repository.RoleRepository;
import br.com.vanep.user.enums.AuthProvider;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.OAuthAccountModel;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.OAuthAccountRepository;
import br.com.vanep.user.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OAuthAccountService {

  private final UserRepository users;
  private final OAuthAccountRepository oauthAccounts;
  private final RoleRepository roles;
  private final RegistrationService registrationService;
  private final MessageSource messages;

  public OAuthAccountService(
      UserRepository users,
      OAuthAccountRepository oauthAccounts,
      RoleRepository roles,
      RegistrationService registrationService,
      MessageSource messages) {
    this.users = users;
    this.oauthAccounts = oauthAccounts;
    this.roles = roles;
    this.registrationService = registrationService;
    this.messages = messages;
  }

  @Transactional
  public OAuthResolution resolve(
      AuthProvider provider, String providerUid, String email, boolean emailVerified, String name) {
    Optional<Long> linkedUserId = oauthAccounts.findLinkedUserId(provider.name(), providerUid);
    if (linkedUserId.isPresent()) {
      // @SoftDelete filtra contas removidas: findById não retorna usuário desativado.
      UserModel user =
          users
              .findById(linkedUserId.get())
              .orElseThrow(
                  () ->
                      new OAuth2AuthenticationException(
                          new OAuth2Error(
                              "account_disabled",
                              messages.getMessage(
                                  "auth.grant.account_disabled",
                                  null,
                                  LocaleContextHolder.getLocale()),
                              null)));
      return OAuthResolution.registered(user);
    }

    if (emailVerified && email != null && !email.isBlank()) {
      Optional<UserModel> byEmail = users.findByEmail(email);
      if (byEmail.isPresent()) {
        OAuthAccountModel linked = link(byEmail.get(), provider, providerUid, email);
        return OAuthResolution.registered(linked.getUser());
      }
    }

    return OAuthResolution.pending(provider, providerUid, email, name);
  }

  @Transactional
  public UserModel completeRegistration(
      AuthProvider provider,
      String providerUid,
      String email,
      String name,
      SignupCompletionFields form) {
    UserModel user = new UserModel();
    user.setType(form.getType());
    roles
        .findByRoleName(roleForType(form.getType()))
        .ifPresent(role -> user.setRoleId(role.getId()));
    user.setName(name);
    user.setEmail(email);
    user.setDocument(form.getDocument());
    user.setPhone(form.getPhone());
    user.setBirthDate(form.getBirthDate());
    user.setGender(form.getGender());
    user.setVerified(true);
    user.setTermsAcceptedAt(Instant.now());
    users.save(user);

    registrationService.createRoleRecord(user, form.getType(), form);

    link(user, provider, providerUid, email);
    return user;
  }

  private static RoleName roleForType(UserType type) {
    return switch (type) {
      case CLIENT -> RoleName.CLIENT;
      case DRIVER -> RoleName.DRIVER;
      case ASSISTANT -> RoleName.ASSISTANT;
      case ADMIN -> RoleName.ADMIN;
    };
  }

  private OAuthAccountModel link(
      UserModel user, AuthProvider provider, String providerUid, String email) {
    OAuthAccountModel account = new OAuthAccountModel();
    account.setUser(user);
    account.setProvider(provider);
    account.setProviderUid(providerUid);
    account.setEmail(email);
    return oauthAccounts.save(account);
  }
}
