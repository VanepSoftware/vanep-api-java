package br.com.vanep.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.RSAKey;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class RsaKeysTest {

  @Test
  void loadsKeyFromPemRoundTrip() throws Exception {
    KeyPair keyPair = newKeyPair();
    String privatePem = pem("PRIVATE KEY", keyPair.getPrivate().getEncoded());
    String publicPem = pem("PUBLIC KEY", keyPair.getPublic().getEncoded());

    RSAKey key = RsaKeys.fromPem(privatePem, publicPem, "kid-1");

    assertThat(key.getKeyID()).isEqualTo("kid-1");
    assertThat(key.toRSAPublicKey()).isEqualTo(keyPair.getPublic());
    assertThat(key.isPrivate()).isTrue();
  }

  @Test
  void loadsKeyFromBase64EncodedPem() throws Exception {
    KeyPair keyPair = newKeyPair();
    String privatePem = pem("PRIVATE KEY", keyPair.getPrivate().getEncoded());
    String publicPem = pem("PUBLIC KEY", keyPair.getPublic().getEncoded());

    RSAKey key = RsaKeys.fromPem(base64(privatePem), base64(publicPem), "kid-b64");

    assertThat(key.getKeyID()).isEqualTo("kid-b64");
    assertThat(key.toRSAPublicKey()).isEqualTo(keyPair.getPublic());
    assertThat(key.isPrivate()).isTrue();
  }

  @Test
  void generateProducesUsableKey() {
    RSAKey key = RsaKeys.generate("kid-2");
    assertThat(key.getKeyID()).isEqualTo("kid-2");
    assertThat(key.isPrivate()).isTrue();
  }

  @Test
  void invalidPemThrows() {
    assertThatThrownBy(() -> RsaKeys.fromPem("not-a-key", "not-a-key", "kid"))
        .isInstanceOf(IllegalStateException.class);
  }

  private static String base64(String pem) {
    return Base64.getEncoder()
        .encodeToString(pem.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private static KeyPair newKeyPair() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    return generator.generateKeyPair();
  }

  private static String pem(String type, byte[] der) {
    return "-----BEGIN "
        + type
        + "-----\n"
        + Base64.getMimeEncoder().encodeToString(der)
        + "\n-----END "
        + type
        + "-----\n";
  }
}
