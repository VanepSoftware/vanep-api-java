package br.com.vanep.user.enums;

/**
 * Passos que faltam ao usuário para conseguir usar o produto.
 *
 * <p>Enum backed em vez de flags booleanas (regra 14, D10): o mobile só percorre a lista e mostra a
 * tela correspondente, sem precisar saber que {@link #SERVICE_AREA} vale apenas para motorista. Um
 * passo novo entra sem quebrar o app.
 */
public enum OnboardingStep {
  /** Falta o endereço residencial — vale para qualquer papel. */
  PERSONAL_ADDRESS("PERSONAL_ADDRESS"),

  /** Falta declarar ao menos uma região de atuação — só motorista. */
  SERVICE_AREA("SERVICE_AREA");

  private final String value;

  OnboardingStep(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }
}
