package br.com.vanep.auth.security;

/**
 * Password hashing bound to a stable per-user identity. The identity is an input to the pepper, not
 * a value stored inside the hash: a hash copied onto another account must not authenticate.
 */
public interface UserPasswordEncoder {

  String encode(CharSequence rawPassword, String userToken);

  boolean matches(CharSequence rawPassword, String encodedPassword, String userToken);

  boolean requiresUpgrade(String encodedPassword);
}
