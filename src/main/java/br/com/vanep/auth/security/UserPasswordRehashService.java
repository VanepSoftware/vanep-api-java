package br.com.vanep.auth.security;

import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserPasswordRehashService {

  private final UserRepository users;
  private final UserPasswordEncoder passwords;

  public UserPasswordRehashService(UserRepository users, UserPasswordEncoder passwords) {
    this.users = users;
    this.passwords = passwords;
  }

  @Transactional
  public UserDetails storeUpgradedHash(VanepUserDetails user, String upgradedHash) {
    UserModel model = users.findByEmail(user.getUsername()).orElse(null);
    if (model == null || !passwords.requiresUpgrade(model.getPassword())) {
      return user;
    }
    model.setPassword(upgradedHash);
    return user.withPassword(upgradedHash);
  }
}
