package br.com.vanep.auth.security;

import br.com.vanep.user.User;
import br.com.vanep.user.UserRepository;
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
    User user =
        users
            .findByEmailAndDeletedAtIsNull(email)
            .orElseThrow(() -> new UsernameNotFoundException("Conta não encontrada: " + email));

    if (user.getPassword() == null || user.getPassword().isBlank()) {

      throw new UsernameNotFoundException("Conta sem senha local: " + email);
    }

    return org.springframework.security.core.userdetails.User.withUsername(user.getEmail())
        .password(user.getPassword())
        .authorities(new SimpleGrantedAuthority("ROLE_" + user.getType().name()))
        .disabled(!user.isVerified())
        .accountLocked(loginAttempts.isBlocked(user.getEmail()))
        .build();
  }
}
