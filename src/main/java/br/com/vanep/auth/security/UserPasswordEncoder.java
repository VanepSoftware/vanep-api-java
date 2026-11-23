package br.com.vanep.auth.security;

import org.jspecify.annotations.Nullable;

/**
 * Password hashing bound to a stable per-user identity. The identity is an input to the pepper, not
 * a value stored inside the hash: a hash copied onto another account must not authenticate.
 */
public interface UserPasswordEncoder {

  String encode(CharSequence rawPassword, String userToken);

  boolean matches(
      CharSequence rawPassword, @Nullable String encodedPassword, @Nullable String userToken);

  boolean requiresUpgrade(@Nullable String encodedPassword);
}
