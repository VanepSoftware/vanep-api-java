package br.com.vanep.places.exception;

public class PlaceLookupException extends RuntimeException {
  public PlaceLookupException(String message, Throwable cause) {
    super(message, cause);
  }

  public PlaceLookupException(String message) {
    super(message);
  }
}
