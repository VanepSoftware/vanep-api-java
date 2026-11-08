package br.com.vanep.auth.security;

import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

public final class VanepUserDetails extends User {

  private final String userToken;

  public VanepUserDetails(
      String username,
      String password,
      String userToken,
      boolean enabled,
      boolean accountNonLocked,
      Collection<? extends GrantedAuthority> authorities) {
    super(username, password, enabled, true, true, accountNonLocked, authorities);
    this.userToken = userToken;
  }

  public String getUserToken() {
    return userToken;
  }

  public VanepUserDetails withPassword(String password) {
    return new VanepUserDetails(
        getUsername(), password, userToken, isEnabled(), isAccountNonLocked(), getAuthorities());
  }
}
