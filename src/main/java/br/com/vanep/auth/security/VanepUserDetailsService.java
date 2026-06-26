package br.com.vanep.auth.security;

import br.com.vanep.user.User;
import br.com.vanep.user.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Carrega contas (por e-mail) para o login local com senha. */
@Service
public class VanepUserDetailsService implements UserDetailsService {

  private final UserRepository users;

  public VanepUserDetailsService(UserRepository users) {
    this.users = users;
  }

  @Override
  public UserDetails loadUserByUsername(String email) {
    User user =
        users
            .findByEmailAndDeletedAtIsNull(email)
            .orElseThrow(() -> new UsernameNotFoundException("Conta não encontrada: " + email));

    if (user.getPassword() == null || user.getPassword().isBlank()) {
      // Contas só-OAuth não têm senha local — não podem logar por senha.
      throw new UsernameNotFoundException("Conta sem senha local: " + email);
    }

    return org.springframework.security.core.userdetails.User.withUsername(user.getEmail())
        .password(user.getPassword())
        .authorities(new SimpleGrantedAuthority("ROLE_" + user.getType().name()))
        .build();
  }
}
