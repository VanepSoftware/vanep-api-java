package br.com.vanep.auth.oauth.grant;

import org.springframework.security.oauth2.core.AuthorizationGrantType;

public final class MobileAuthorizationGrantTypes {

  public static final AuthorizationGrantType PASSWORD =
      new AuthorizationGrantType("urn:vanep:params:oauth:grant-type:password");

  public static final AuthorizationGrantType GOOGLE =
      new AuthorizationGrantType("urn:vanep:params:oauth:grant-type:google");

  private MobileAuthorizationGrantTypes() {}
}
