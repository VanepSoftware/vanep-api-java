package br.com.vanep.places.exception;

import java.io.Serial;

public class PlaceNotFoundException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final String placeId;

  public PlaceNotFoundException(String placeId) {
    super("Place not found in Google Places: " + placeId);
    this.placeId = placeId;
  }

  public String getPlaceId() {
    return placeId;
  }
}
