package br.com.vanep.auth.oauth;

import br.com.vanep.user.AuthProvider;
import br.com.vanep.user.User;

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
