package br.com.vanep.places.exception;

/**
 * O {@code placeId} enviado não existe no Google. É erro do dado de entrada, não da integração —
 * ver {@link PlaceLookupException} para o outro caso.
 */
public class PlaceNotFoundException extends RuntimeException {

  private final String placeId;

  public PlaceNotFoundException(String placeId) {
    super("Place não encontrado no Google Places: " + placeId);
    this.placeId = placeId;
  }

  public String getPlaceId() {
    return placeId;
  }
}
