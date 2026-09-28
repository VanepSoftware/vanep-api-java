package br.com.vanep.cep.exception;

public class ViaCepLookupException extends RuntimeException {
  public ViaCepLookupException(String message, Throwable cause) {
    super(message, cause);
  }

  public ViaCepLookupException(String message) {
    super(message);
  }
}
