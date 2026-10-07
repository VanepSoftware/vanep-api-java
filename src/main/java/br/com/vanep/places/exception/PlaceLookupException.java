package br.com.vanep.places.exception;

import java.io.Serial;

public class PlaceLookupException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public PlaceLookupException(String message, Throwable cause) {
    super(message, cause);
  }

  public PlaceLookupException(String message) {
    super(message);
  }
}
