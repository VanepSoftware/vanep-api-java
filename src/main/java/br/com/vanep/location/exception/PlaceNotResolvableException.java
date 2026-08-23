package br.com.vanep.location.exception;

/**
 * O place existe no Google mas não traz os componentes mínimos da árvore (país, estado e cidade).
 * Acontece com resultados amplos demais — uma região inteira, um país — que não servem como origem,
 * destino nem endereço.
 */
public class PlaceNotResolvableException extends RuntimeException {

  private final String missingLevel;

  public PlaceNotResolvableException(String missingLevel) {
    super("Place sem componente de nível " + missingLevel + ".");
    this.missingLevel = missingLevel;
  }

  public String getMissingLevel() {
    return missingLevel;
  }
}
