package br.com.vanep.auth.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

public class PepperedArgon2PasswordEncoder implements PasswordEncoder {

  private static final String HMAC_ALGORITHM = "HmacSHA256";

  private final SecretKeySpec pepperKey;
  private final PasswordEncoder delegate;

  public PepperedArgon2PasswordEncoder(String pepper) {
    if (pepper == null || pepper.isBlank()) {
      throw new IllegalStateException(
          "VANEP_PASSWORD_PEPPER deve ser configurado (vanep.password.pepper).");
    }
    this.pepperKey = new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);

    this.delegate = new Argon2PasswordEncoder(16, 32, 1, 19 * 1024, 2);
  }

  @Override
  public String encode(CharSequence rawPassword) {
    return delegate.encode(applyPepper(rawPassword));
  }

  @Override
  public boolean matches(CharSequence rawPassword, String encodedPassword) {
    if (encodedPassword == null || encodedPassword.isBlank()) {
      return false;
    }
    return delegate.matches(applyPepper(rawPassword), encodedPassword);
  }

  private String applyPepper(CharSequence rawPassword) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(pepperKey);
      byte[] digest = mac.doFinal(rawPassword.toString().getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(digest);
    } catch (Exception ex) {
      throw new IllegalStateException("Falha ao aplicar o pepper na senha.", ex);
    }
  }
}
