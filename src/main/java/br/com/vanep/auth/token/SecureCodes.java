package br.com.vanep.auth.token;

import br.com.vanep.auth.enums.AuthCodePurpose;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SecureCodes {

  private static final String ALGORITHM = "HmacSHA256";
  private static final SecureRandom RANDOM = new SecureRandom();

  private final byte[] pepper;

  public SecureCodes(@Value("${vanep.password.pepper}") String pepper) {
    this.pepper = pepper.getBytes(StandardCharsets.UTF_8);
  }

  public String generate() {
    return "%06d".formatted(RANDOM.nextInt(1_000_000));
  }

  public String hmac(AuthCodePurpose purpose, Long userId, String code) {
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(new SecretKeySpec(pepper, ALGORITHM));
      String payload = purpose.name() + "|" + userId + "|" + code;
      return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to derive the auth code hash.", ex);
    }
  }

  public boolean matches(String storedHash, AuthCodePurpose purpose, Long userId, String code) {
    if (storedHash == null || code == null) {
      return false;
    }
    return MessageDigest.isEqual(
        storedHash.getBytes(StandardCharsets.UTF_8),
        hmac(purpose, userId, code).getBytes(StandardCharsets.UTF_8));
  }
}
