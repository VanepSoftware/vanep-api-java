package br.com.vanep.location.exception;

import java.io.Serial;

public class UnsupportedCountryException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final String isoCode;

  public UnsupportedCountryException(String isoCode) {
    super("Unsupported country: " + isoCode);
    this.isoCode = isoCode;
  }

  public String getIsoCode() {
    return isoCode;
  }
}
