package br.com.vanep.auth.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Geração e hashing de tokens de uso único (verificação de e-mail, reset de senha).
 *
 * <p>O token enviado por e-mail é aleatório (256 bits); no banco guardamos apenas o SHA-256 dele —
 * assim um vazamento do banco não expõe tokens utilizáveis.
 */
public final class SecureTokens {

  private static final SecureRandom RANDOM = new SecureRandom();

  private SecureTokens() {}

  /** Token opaco URL-safe (256 bits de entropia) para enviar no link. */
  public static String generate() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /** SHA-256 (hex) do token bruto — o que vai persistido. */
  public static String hash(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new IllegalStateException("Falha ao gerar hash do token.", ex);
    }
  }
}
