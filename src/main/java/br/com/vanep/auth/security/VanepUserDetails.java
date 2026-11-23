package br.com.vanep.auth.security;

import java.util.Collection;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

public final class VanepUserDetails extends User {

  private final @Nullable String userToken;

  public VanepUserDetails(
      String username,
      @Nullable String password,
      @Nullable String userToken,
      boolean enabled,
      boolean accountNonLocked,
      Collection<? extends GrantedAuthority> authorities) {
    this(username, password, userToken, enabled, true, true, accountNonLocked, authorities);
  }

  public VanepUserDetails(
      String username,
      @Nullable String password,
      @Nullable String userToken,
      boolean enabled,
      boolean accountNonExpired,
      boolean credentialsNonExpired,
      boolean accountNonLocked,
      Collection<? extends GrantedAuthority> authorities) {
    super(
        username,
        password,
        enabled,
        accountNonExpired,
        credentialsNonExpired,
        accountNonLocked,
        authorities);
    this.userToken = userToken;
  }

  public @Nullable String getUserToken() {
    return userToken;
  }

  public VanepUserDetails withPassword(String password) {
    return new VanepUserDetails(
        getUsername(),
        password,
        userToken,
        isEnabled(),
        isAccountNonExpired(),
        isCredentialsNonExpired(),
        isAccountNonLocked(),
        getAuthorities());
  }
}
