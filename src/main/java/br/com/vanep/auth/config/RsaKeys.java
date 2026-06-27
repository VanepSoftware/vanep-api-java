package br.com.vanep.auth.config;

import com.nimbusds.jose.jwk.RSAKey;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Carrega/gera o par de chaves RSA usado para assinar os JWTs do Authorization Server.
 *
 * <p>Em produção as chaves devem vir da configuração ({@code vanep.oauth.jwk.*}) e ser estáveis: do
 * contrário cada restart invalida todos os tokens e instâncias diferentes assinam com chaves
 * diferentes (a validação falha atrás de um load balancer).
 */
final class RsaKeys {

  private RsaKeys() {}

  /** Constrói a {@link RSAKey} a partir de PEMs (PKCS#8 para a privada, X.509 para a pública). */
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

  /** Gera um par RSA efêmero — apenas para desenvolvimento. */
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
    String base64 =
        pem.replaceAll("-----BEGIN (.*)-----", "")
            .replaceAll("-----END (.*)-----", "")
            .replaceAll("\\s", "");
    return Base64.getDecoder().decode(base64);
  }
}
