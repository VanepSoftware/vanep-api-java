package br.com.vanep.location.exception;

/**
 * The place's country is not registered. {@code country} and {@code state} are the curated levels
 * of the tree — city and district are born on demand — so a missing country is a business decision
 * ("we don't operate here"), not missing data.
 */
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
