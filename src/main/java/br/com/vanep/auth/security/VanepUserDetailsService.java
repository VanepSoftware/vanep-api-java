package br.com.vanep.auth.security;

import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class VanepUserDetailsService implements UserDetailsService {

  private final UserRepository users;
  private final LoginAttemptService loginAttempts;

  public VanepUserDetailsService(UserRepository users, LoginAttemptService loginAttempts) {
    this.users = users;
    this.loginAttempts = loginAttempts;
  }

  @Override
  public UserDetails loadUserByUsername(String email) {
    // Checked before the lookup so a locked e-mail answers the same way whether or not an
    // account exists: DaoAuthenticationProvider hides UsernameNotFoundException, not this one.
    if (loginAttempts.isBlocked(email)) {
      throw new LockedException("Too many failed attempts for " + email);
    }
    UserModel user =
        users
            .findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("Account not found: " + email));

    if (user.getPassword() == null || user.getPassword().isBlank()) {
      throw new UsernameNotFoundException("Account without a local password: " + email);
    }

    return org.springframework.security.core.userdetails.User.withUsername(user.getEmail())
        .password(user.getPassword())
        .authorities(new SimpleGrantedAuthority("ROLE_" + user.getType().name()))
        .disabled(!user.isVerified())
        .accountLocked(loginAttempts.isBlocked(user.getEmail()))
        .build();
  }
}
