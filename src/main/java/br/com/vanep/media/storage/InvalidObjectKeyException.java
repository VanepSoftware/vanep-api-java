package br.com.vanep.media.storage;

import java.io.Serial;

public class InvalidObjectKeyException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public InvalidObjectKeyException(String message) {
    super(message);
  }
}
