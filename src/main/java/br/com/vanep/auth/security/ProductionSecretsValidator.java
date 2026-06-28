package br.com.vanep.auth.security;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProductionSecretsValidator implements InitializingBean {

  private static final String DEFAULT_REMEMBER_ME_KEY = "vanep-remember-me-change-me";
  private static final String DEFAULT_PEPPER = "dev-pepper-please-change";

  private final String rememberMeKey;
  private final String pepper;
  private final String jwkPrivateKey;

  public ProductionSecretsValidator(
      @Value("${vanep.remember-me.key:}") String rememberMeKey,
      @Value("${vanep.password.pepper:}") String pepper,
      @Value("${vanep.oauth.jwk.private-key:}") String jwkPrivateKey) {
    this.rememberMeKey = rememberMeKey;
    this.pepper = pepper;
    this.jwkPrivateKey = jwkPrivateKey;
  }

  @Override
  public void afterPropertiesSet() {
    requireSecret("vanep.remember-me.key", rememberMeKey, DEFAULT_REMEMBER_ME_KEY);
    requireSecret("vanep.password.pepper", pepper, DEFAULT_PEPPER);
    if (jwkPrivateKey == null || jwkPrivateKey.isBlank()) {
      throw new IllegalStateException(
          "Em produção configure vanep.oauth.jwk.private-key (chave de assinatura RSA estável).");
    }
  }

  private static void requireSecret(String name, String value, String insecureDefault) {
    if (value == null || value.isBlank() || value.equals(insecureDefault)) {
      throw new IllegalStateException(
          "Em produção configure um valor seguro para " + name + " (não pode ficar no default).");
    }
  }
}
