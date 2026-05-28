package br.com.vanep.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class TokenGenerator {

  private TokenGenerator() {}

  public static String generate() {
    String input =
        System.nanoTime() + ThreadLocalRandom.current().nextLong() + UUID.randomUUID().toString();
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-1");
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash).substring(0, 25);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-1 not available", e);
    }
  }
}
