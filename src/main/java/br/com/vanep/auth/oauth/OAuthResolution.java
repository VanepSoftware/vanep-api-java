package br.com.vanep.auth.oauth;

import br.com.vanep.user.AuthProvider;
import br.com.vanep.user.User;

/**
 * Resultado de resolver um login social.
 *
 * <ul>
 *   <li><b>registered</b> — a conta já existe (vínculo encontrado ou criado por e-mail); {@code
 *       user} preenchido.
 *   <li>caso contrário — cadastro pendente: o usuário precisa completar o passo 2.
 * </ul>
 */
public record OAuthResolution(
    boolean registered,
    User user,
    AuthProvider provider,
    String providerUid,
    String email,
    String name) {

  public static OAuthResolution registered(User user) {
    return new OAuthResolution(true, user, null, null, null, null);
  }

  public static OAuthResolution pending(
      AuthProvider provider, String providerUid, String email, String name) {
    return new OAuthResolution(false, null, provider, providerUid, email, name);
  }
}
