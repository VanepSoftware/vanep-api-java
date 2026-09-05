package br.com.vanep.places.exception;

public class PlaceNotFoundException extends RuntimeException {
  private final String placeId;

  public PlaceNotFoundException(String placeId) {
    super("Place not found in Google Places: " + placeId);
    this.placeId = placeId;
  }

  public String getPlaceId() {
    return placeId;
  }
}
