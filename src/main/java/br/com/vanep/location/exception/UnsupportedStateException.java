package br.com.vanep.location.exception;

public class UnsupportedStateException extends RuntimeException {
  private final String uf;

  public UnsupportedStateException(String uf) {
    super("Unsupported state: " + uf);
    this.uf = uf;
  }

  public String getUf() {
    return uf;
  }
}
