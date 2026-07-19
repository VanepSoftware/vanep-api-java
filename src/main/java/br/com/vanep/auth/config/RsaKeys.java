package br.com.vanep.auth.config;

import com.nimbusds.jose.jwk.RSAKey;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class RsaKeys {

  private static final Logger log = LoggerFactory.getLogger(RsaKeys.class);

  private RsaKeys() {}

  /**
   * Loads a persisted RSA JWK (including its private key) from {@code path}, generating and saving
   * a new one when the file is missing or unreadable. Used only in dev so that issued tokens keep
   * validating across application restarts. Never use for production keys.
   */
  static RSAKey loadOrCreate(String keyId, Path path) {
    try {
      if (Files.isReadable(path)) {
        return RSAKey.parse(Files.readString(path, StandardCharsets.UTF_8));
      }
    } catch (Exception ex) {
      log.warn("Não foi possível ler a JWK de dev em {}; gerando uma nova.", path, ex);
    }

    RSAKey generated = generate(keyId);
    try {
      Path parent = path.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.writeString(path, generated.toJSONString(), StandardCharsets.UTF_8);
    } catch (Exception ex) {
      log.warn(
          "Não foi possível persistir a JWK de dev em {}; tokens não sobreviverão a restart.",
          path,
          ex);
    }
    return generated;
  }

  static RSAKey fromPem(String privateKeyPem, String publicKeyPem, String keyId) {
    try {
      KeyFactory factory = KeyFactory.getInstance("RSA");
      RSAPrivateKey privateKey =
          (RSAPrivateKey)
              factory.generatePrivate(new PKCS8EncodedKeySpec(decodePem(privateKeyPem)));
      RSAPublicKey publicKey =
          (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(decodePem(publicKeyPem)));
      return new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(keyId).build();
    } catch (Exception ex) {
      throw new IllegalStateException(
          "Não foi possível carregar a chave RSA de vanep.oauth.jwk.* (verifique os PEMs).", ex);
    }
  }

  static RSAKey generate(String keyId) {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      KeyPair keyPair = generator.generateKeyPair();
      return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
          .privateKey((RSAPrivateKey) keyPair.getPrivate())
          .keyID(keyId)
          .build();
    } catch (Exception ex) {
      throw new IllegalStateException("Não foi possível gerar a chave RSA.", ex);
    }
  }

  private static byte[] decodePem(String pem) {
    String source = pem.trim();
    // Se não começa com "-----", assume base64 do PEM inteiro (ex.: `base64 -w0 private.pem`).
    if (!source.startsWith("-----")) {
      source = new String(Base64.getDecoder().decode(source), StandardCharsets.UTF_8);
    }
    String base64 =
        source
            .replaceAll("-----BEGIN (.*)-----", "")
            .replaceAll("-----END (.*)-----", "")
            .replaceAll("\\s", "");
    return Base64.getDecoder().decode(base64);
  }
}
