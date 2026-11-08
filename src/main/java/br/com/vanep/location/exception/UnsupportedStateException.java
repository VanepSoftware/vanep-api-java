package br.com.vanep.location.exception;

import java.io.Serial;

public class UnsupportedStateException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final String uf;

  public UnsupportedStateException(String uf) {
    super("Unsupported state: " + uf);
    this.uf = uf;
  }

  public String getUf() {
    return uf;
  }
}
