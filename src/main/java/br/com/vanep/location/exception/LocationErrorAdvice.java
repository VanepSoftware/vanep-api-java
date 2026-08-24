package br.com.vanep.location.exception;

import br.com.vanep.places.exception.PlaceLookupException;
import br.com.vanep.places.exception.PlaceNotFoundException;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduz as falhas de resolução de lugar em respostas HTTP, com mensagem pt-BR via MessageSource
 * (regra 46).
 *
 * <p>A separação entre 4xx e 5xx segue a origem do problema, não o código que o Google devolveu:
 * {@code placeId} ruim é culpa do dado que o cliente enviou; credencial, quota ou fornecedor fora
 * do ar é problema nosso, e mentir sobre isso mandaria o usuário caçar um erro que não é dele.
 */
@RestControllerAdvice
public class LocationErrorAdvice {

  private final MessageSource messages;

  public LocationErrorAdvice(MessageSource messages) {
    this.messages = messages;
  }

  @ExceptionHandler(PlaceNotFoundException.class)
  public ProblemDetail handlePlaceNotFound(PlaceNotFoundException exception) {
    return problem(HttpStatus.BAD_REQUEST, "location.place.not_found");
  }

  @ExceptionHandler(PlaceNotResolvableException.class)
  public ProblemDetail handleNotResolvable(PlaceNotResolvableException exception) {
    return problem(HttpStatus.BAD_REQUEST, "location.place.not_resolvable");
  }

  @ExceptionHandler(UnsupportedCountryException.class)
  public ProblemDetail handleUnsupportedCountry(UnsupportedCountryException exception) {
    return problem(HttpStatus.BAD_REQUEST, "location.country.unsupported");
  }

  @ExceptionHandler(UnknownAddressComponentException.class)
  public ProblemDetail handleUnknownComponent(UnknownAddressComponentException exception) {
    return problem(HttpStatus.BAD_REQUEST, "location.component.unknown_type");
  }

  @ExceptionHandler(PlaceLookupException.class)
  public ProblemDetail handleLookupFailure(PlaceLookupException exception) {
    return problem(HttpStatus.SERVICE_UNAVAILABLE, "location.place.lookup_failed");
  }

  private ProblemDetail problem(HttpStatus status, String key) {
    return ProblemDetail.forStatusAndDetail(
        status, messages.getMessage(key, null, LocaleContextHolder.getLocale()));
  }
}
