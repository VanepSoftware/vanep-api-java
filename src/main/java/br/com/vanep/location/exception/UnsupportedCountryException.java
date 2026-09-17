package br.com.vanep.location.exception;

public class UnsupportedCountryException extends RuntimeException {
  private final String isoCode;

  public UnsupportedCountryException(String isoCode) {
    super("Unsupported country: " + isoCode);
    this.isoCode = isoCode;
  }

  public String getIsoCode() {
    return isoCode;
  }
}
