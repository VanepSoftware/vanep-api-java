package br.com.vanep.conversation.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ConversationMessageCipher {

  static final String FORMAT_PREFIX = "v1:";
  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int IV_BYTES = 12;
  private static final int TAG_BITS = 128;
  private static final String KEY_PATTERN = "[0-9a-fA-F]{64}";

  private final SecretKey key;
  private final SecureRandom random = new SecureRandom();

  public ConversationMessageCipher(@Value("${vanep.chat.encryption-key:}") String hexKey) {
    this.key = new SecretKeySpec(parseKey(hexKey), "AES");
  }

  static byte[] parseKey(String hexKey) {
    if (hexKey == null || !hexKey.matches(KEY_PATTERN)) {
      throw new IllegalStateException(
          "vanep.chat.encryption-key must be 64 hex characters (openssl rand -hex 32).");
    }
    return HexFormat.of().parseHex(hexKey);
  }

  public String encrypt(String plaintext) {
    byte[] iv = new byte[IV_BYTES];
    random.nextBytes(iv);
    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
      byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      byte[] payload = ByteBuffer.allocate(iv.length + sealed.length).put(iv).put(sealed).array();
      return FORMAT_PREFIX + Base64.getEncoder().encodeToString(payload);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Could not encrypt the message body", e);
    }
  }

  public String decrypt(String stored) {
    // Bodies written before encryption shipped have no prefix and are plaintext already.
    if (!stored.startsWith(FORMAT_PREFIX)) {
      return stored;
    }
    try {
      byte[] payload = Base64.getDecoder().decode(stored.substring(FORMAT_PREFIX.length()));
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, payload, 0, IV_BYTES));
      byte[] plaintext = cipher.doFinal(payload, IV_BYTES, payload.length - IV_BYTES);
      return new String(plaintext, StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw new IllegalStateException("Could not decrypt the message body", e);
    }
  }
}
