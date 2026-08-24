package br.com.vanep.location;

import br.com.vanep.places.dto.AddressComponentDTO;
import br.com.vanep.places.dto.PlaceDetailsResponseDTO;
import java.util.Optional;

/**
 * Lê os componentes de logradouro de um place — rua, número e CEP.
 *
 * <p>Separado do {@link AddressComponentClassifier} de propósito: aquele monta a árvore
 * compartilhada, este lê o que é privado do endereço e nunca entra na árvore. Misturar os dois
 * colocaria logradouro em nó público, que é exatamente o vazamento que o D1 evita pelo schema.
 */
public final class StreetAddressExtractor {

  private static final String ROUTE = "route";
  private static final String STREET_NUMBER = "street_number";
  private static final String POSTAL_CODE = "postal_code";

  private StreetAddressExtractor() {}

  public static Optional<String> findStreet(PlaceDetailsResponseDTO details) {
    return findLongText(details, ROUTE);
  }

  public static Optional<String> findNumber(PlaceDetailsResponseDTO details) {
    return findLongText(details, STREET_NUMBER);
  }

  /** Nulo é comum: places reais do DF vêm sem {@code postal_code}. */
  public static Optional<String> findZipCode(PlaceDetailsResponseDTO details) {
    return findLongText(details, POSTAL_CODE).map(zip -> zip.replaceAll("\\D", ""));
  }

  private static Optional<String> findLongText(PlaceDetailsResponseDTO details, String type) {
    return details.addressComponents().stream()
        .filter(component -> component.types().contains(type))
        .map(AddressComponentDTO::longText)
        .filter(text -> text != null && !text.isBlank())
        .findFirst();
  }
}
