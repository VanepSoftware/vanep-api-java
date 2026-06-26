package br.com.vanep.auth.oauth;

import br.com.vanep.user.AuthProvider;
import br.com.vanep.user.User;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

/**
 * Carrega o usuário OIDC (Google) e o cruza com as contas Vanep:
 *
 * <ul>
 *   <li>conta existente → autoridade {@code ROLE_<type>}, nome = e-mail (vira o {@code sub} do
 *       JWT);
 *   <li>sem conta → autoridade {@code ROLE_PRE_REGISTER} (cadastro em 2 passos pendente).
 * </ul>
 */
@Service
public class VanepOidcUserService extends OidcUserService {

  /** Autoridade temporária de quem logou no social mas ainda não completou o cadastro. */
  public static final String ROLE_PRE_REGISTER = "ROLE_PRE_REGISTER";

  private final OAuthAccountService accounts;

  public VanepOidcUserService(OAuthAccountService accounts) {
    this.accounts = accounts;
  }

  @Override
  public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
    OidcUser oidcUser = super.loadUser(userRequest);

    AuthProvider provider = providerOf(userRequest.getClientRegistration().getRegistrationId());
    String providerUid = oidcUser.getSubject();
    String email = oidcUser.getEmail();
    String name = oidcUser.getFullName() != null ? oidcUser.getFullName() : email;

    OAuthResolution resolution = accounts.resolve(provider, providerUid, email, name);

    Map<String, Object> claims = new HashMap<>(oidcUser.getClaims());
    if (resolution.registered()) {
      User user = resolution.user();
      claims.put("email", user.getEmail());
      List<GrantedAuthority> authorities =
          List.of(new SimpleGrantedAuthority("ROLE_" + user.getType().name()));
      return new DefaultOidcUser(
          authorities, oidcUser.getIdToken(), new OidcUserInfo(claims), "email");
    }

    claims.put("email", email);
    List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(ROLE_PRE_REGISTER));
    return new DefaultOidcUser(
        authorities, oidcUser.getIdToken(), new OidcUserInfo(claims), "email");
  }

  private static AuthProvider providerOf(String registrationId) {
    return switch (registrationId) {
      case "google" -> AuthProvider.GOOGLE;
      case "apple" -> AuthProvider.APPLE;
      default -> throw new IllegalStateException("Provedor OAuth desconhecido: " + registrationId);
    };
  }
}
