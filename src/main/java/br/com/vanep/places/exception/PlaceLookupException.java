package br.com.vanep.places.exception;

/**
 * Google Places could not be queried: network error, timeout, rejected credential, or unexpected
 * response.
 *
 * <p>Distinct from {@link PlaceNotFoundException}: here the problem is ours or the provider's, not
 * the {@code placeId} the client sent. Whoever handles it needs to know the difference — one
 * becomes a 5xx, the other a 4xx.
 */
public class PlaceLookupException extends RuntimeException {

  public PlaceLookupException(String message, Throwable cause) {
    super(message, cause);
  }

  public PlaceLookupException(String message) {
    super(message);
  }
}
