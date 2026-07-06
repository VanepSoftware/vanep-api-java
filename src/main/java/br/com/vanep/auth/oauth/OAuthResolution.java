package br.com.vanep.auth.oauth;

import br.com.vanep.user.AuthProvider;
import br.com.vanep.user.model.UserModel;

public record OAuthResolution(
    boolean registered,
    UserModel user,
    AuthProvider provider,
    String providerUid,
    String email,
    String name) {

  public static OAuthResolution registered(UserModel user) {
    return new OAuthResolution(true, user, null, null, null, null);
  }

  public static OAuthResolution pending(
      AuthProvider provider, String providerUid, String email, String name) {
    return new OAuthResolution(false, null, provider, providerUid, email, name);
  }
}
