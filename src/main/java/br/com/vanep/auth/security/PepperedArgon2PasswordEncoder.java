package br.com.vanep.auth.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

public class PepperedArgon2PasswordEncoder implements PasswordEncoder, UserPasswordEncoder {

  private static final String HMAC_ALGORITHM = "HmacSHA256";

  // Scheme marker only. The user token is a pepper input, not stored here: a copied hash must
  // not carry the account it was created for.
  private static final String BOUND_PREFIX = "bound$";

  private static final byte[] USER_BINDING = "vanep-user-v1".getBytes(StandardCharsets.UTF_8);

  private final SecretKeySpec pepperKey;
  private final PasswordEncoder delegate;

  public PepperedArgon2PasswordEncoder(String pepper) {
    if (pepper == null || pepper.isBlank()) {
      throw new IllegalStateException(
          "VANEP_PASSWORD_PEPPER must be configured (vanep.password.pepper).");
    }
    this.pepperKey = new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);

    this.delegate = new Argon2PasswordEncoder(16, 32, 1, 19 * 1024, 2);
  }

  @Override
  public String encode(CharSequence rawPassword) {
    return this.delegate.encode(applyPepper(rawPassword));
  }

  @Override
  public String encode(CharSequence rawPassword, String userToken) {
    if (rawPassword == null) {
      throw new IllegalArgumentException("rawPassword is required");
    }
    if (userToken == null || userToken.isBlank()) {
      throw new IllegalArgumentException("userToken is required to encode a password");
    }
    return BOUND_PREFIX + this.delegate.encode(applyBoundPepper(rawPassword, userToken));
  }

  @Override
  public boolean matches(CharSequence rawPassword, String encodedPassword) {
    if (rawPassword == null
        || encodedPassword == null
        || encodedPassword.isBlank()
        || isBound(encodedPassword)) {
      return false;
    }
    return this.delegate.matches(applyPepper(rawPassword), encodedPassword);
  }

  @Override
  public boolean matches(CharSequence rawPassword, String encodedPassword, String userToken) {
    if (rawPassword == null || encodedPassword == null || encodedPassword.isBlank()) {
      return false;
    }
    if (isBound(encodedPassword)) {
      if (userToken == null || userToken.isBlank()) {
        return false;
      }
      return this.delegate.matches(
          applyBoundPepper(rawPassword, userToken), stripBoundPrefix(encodedPassword));
    }
    // Hashes stored before the user binding have no prefix. They still verify, and a successful
    // login replaces them with a bound hash.
    return matches(rawPassword, encodedPassword);
  }

  @Override
  public boolean requiresUpgrade(String encodedPassword) {
    return encodedPassword != null && !encodedPassword.isBlank() && !isBound(encodedPassword);
  }

  private static boolean isBound(String encodedPassword) {
    return encodedPassword.startsWith(BOUND_PREFIX);
  }

  private static String stripBoundPrefix(String encodedPassword) {
    return encodedPassword.substring(BOUND_PREFIX.length());
  }

  private String applyPepper(CharSequence rawPassword) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(this.pepperKey);
      byte[] digest = mac.doFinal(rawPassword.toString().getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(digest);
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to apply the password pepper.", ex);
    }
  }

  private String applyBoundPepper(CharSequence rawPassword, String userToken) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(this.pepperKey);
      mac.update(USER_BINDING);
      mac.update((byte) 0);
      mac.update(userToken.getBytes(StandardCharsets.UTF_8));
      mac.update((byte) 0);
      byte[] digest = mac.doFinal(rawPassword.toString().getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(digest);
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to bind the password hash.", ex);
    }
  }
}
