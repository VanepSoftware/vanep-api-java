package br.com.vanep.places.exception;

/**
 * The given {@code placeId} does not exist on Google. It's an input error, not an integration
 * error — see {@link PlaceLookupException} for the other case.
 */
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
