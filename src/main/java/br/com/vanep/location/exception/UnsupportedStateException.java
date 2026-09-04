package br.com.vanep.location.exception;

/**
 * The place's state (UF) is not registered. Brazil has a closed set of 27 units, seeded once by
 * {@code StateSeeder}, so a missing UF means the place is outside what we serve — not data waiting
 * to be created.
 *
 * <p>The resolver used to create the row on demand. It could not: {@code requires_district} (D8) is
 * curated per UF, and a lazily created row would be born with the wrong flag — including the
 * Distrito Federal, which is the launch market and the whole reason D8 exists.
 */
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
