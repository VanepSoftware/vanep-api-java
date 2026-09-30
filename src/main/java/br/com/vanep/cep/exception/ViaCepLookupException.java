package br.com.vanep.cep.exception;

import java.io.Serial;

public class ViaCepLookupException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public ViaCepLookupException(String message, Throwable cause) {
    super(message, cause);
  }

  public ViaCepLookupException(String message) {
    super(message);
  }
}
