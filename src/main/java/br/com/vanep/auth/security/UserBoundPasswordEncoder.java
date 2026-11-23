package br.com.vanep.auth.security;

import org.jspecify.annotations.Nullable;

/**
 * User-password hashing. Separate from the {@link
 * org.springframework.security.crypto.password.PasswordEncoder} bean, which hashes the OAuth client
 * secret and has no account to bind.
 */
public final class UserBoundPasswordEncoder implements UserPasswordEncoder {

  private final PepperedArgon2PasswordEncoder encoder;

  public UserBoundPasswordEncoder(PepperedArgon2PasswordEncoder encoder) {
    this.encoder = encoder;
  }

  @Override
  public String encode(CharSequence rawPassword, String userToken) {
    return this.encoder.encode(rawPassword, userToken);
  }

  @Override
  public boolean matches(
      CharSequence rawPassword, @Nullable String encodedPassword, @Nullable String userToken) {
    return this.encoder.matches(rawPassword, encodedPassword, userToken);
  }

  @Override
  public boolean requiresUpgrade(@Nullable String encodedPassword) {
    return this.encoder.requiresUpgrade(encodedPassword);
  }

  PepperedArgon2PasswordEncoder argon2() {
    return this.encoder;
  }
}
