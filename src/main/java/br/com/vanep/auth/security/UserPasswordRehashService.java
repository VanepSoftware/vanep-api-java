package br.com.vanep.auth.security;

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
    String checked = user.getPassword();
    if (!passwords.requiresUpgrade(checked)) {
      return user;
    }
    // One UPDATE so a reset that committed after the password check is not overwritten.
    int updated = users.upgradePasswordIfCurrent(user.getUsername(), checked, upgradedHash);
    if (updated != 1) {
      return user;
    }
    return user.withPassword(upgradedHash);
  }
}
