package br.com.vanep.location.exception;

public class UnmatchedCityException extends RuntimeException {
  private final String uf;
  private final String googleCityName;
  private final String placeId;

  public UnmatchedCityException(String uf, String googleCityName, String placeId) {
    super(
        "Unmatched Google city '%s' in UF %s (placeId=%s).".formatted(googleCityName, uf, placeId));
    this.uf = uf;
    this.googleCityName = googleCityName;
    this.placeId = placeId;
  }

  public String getUf() {
    return uf;
  }

  public String getGoogleCityName() {
    return googleCityName;
  }

  public String getPlaceId() {
    return placeId;
  }
}
