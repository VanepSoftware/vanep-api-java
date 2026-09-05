package br.com.vanep.location.exception;

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
