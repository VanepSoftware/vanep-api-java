package br.com.vanep.location.exception;

import java.io.Serial;

public class PlaceNotResolvableException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final String missingLevel;

  public PlaceNotResolvableException(String missingLevel) {
    super("Place sem componente de nível " + missingLevel + ".");
    this.missingLevel = missingLevel;
  }

  public String getMissingLevel() {
    return missingLevel;
  }
}
