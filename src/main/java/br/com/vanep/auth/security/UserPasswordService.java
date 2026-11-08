package br.com.vanep.auth.security;

import br.com.vanep.user.model.UserModel;
import org.springframework.stereotype.Service;

@Service
public class UserPasswordService {

  private final UserPasswordEncoder passwords;

  public UserPasswordService(UserPasswordEncoder passwords) {
    this.passwords = passwords;
  }

  public void assignEncodedPassword(UserModel user, CharSequence rawPassword) {
    // @PrePersist assigns the token only when the row is inserted, which is too late to bind it.
    user.assignToken();
    user.setPassword(passwords.encode(rawPassword, user.getToken()));
  }
}
