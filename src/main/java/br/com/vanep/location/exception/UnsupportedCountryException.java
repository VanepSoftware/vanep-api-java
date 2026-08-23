package br.com.vanep.location.exception;

/**
 * O país do place não está cadastrado. {@code country} é o único nível curado da árvore — os demais
 * nascem sob demanda —, então um país ausente é decisão de negócio ("não atendemos aqui"), não dado
 * faltando.
 */
public class UnsupportedCountryException extends RuntimeException {

  private final String isoCode;

  public UnsupportedCountryException(String isoCode) {
    super("País não suportado: " + isoCode);
    this.isoCode = isoCode;
  }

  public String getIsoCode() {
    return isoCode;
  }
}
