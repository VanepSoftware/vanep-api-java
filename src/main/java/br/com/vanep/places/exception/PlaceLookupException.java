package br.com.vanep.places.exception;

/**
 * O Google Places não pôde ser consultado: erro de rede, timeout, credencial rejeitada ou resposta
 * inesperada.
 *
 * <p>Distinta de {@link PlaceNotFoundException}: aqui o problema é nosso ou do fornecedor, não do
 * {@code placeId} que o cliente enviou. Quem trata precisa saber a diferença — uma vira 5xx, a
 * outra vira 4xx.
 */
public class PlaceLookupException extends RuntimeException {

  public PlaceLookupException(String message, Throwable cause) {
    super(message, cause);
  }

  public PlaceLookupException(String message) {
    super(message);
  }
}
