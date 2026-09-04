package br.com.vanep.location;

import br.com.vanep.location.dto.LocationComponentDTO;
import br.com.vanep.places.dto.AddressComponentDTO;
import br.com.vanep.places.dto.PlaceDetailsResponseDTO;
import java.util.List;
import java.util.Optional;

/**
 * Lê os componentes de logradouro de um place — rua, número e CEP.
 *
 * <p>Separado do {@link AddressComponentClassifier} de propósito: aquele monta a árvore
 * compartilhada, este lê o que é privado do endereço e nunca entra na árvore. Misturar os dois
 * colocaria logradouro em nó público, que é exatamente o vazamento que o D1 evita pelo schema.
 */
public final class StreetAddressExtractor {

  /** Profundidade da RA no DF e do bairro em SP: é região, não endereço. */
  private static final int SHALLOWEST_DISTRICT_DEPTH = 1;

  private static final String ROUTE = "route";
  private static final String STREET_NUMBER = "street_number";
  private static final String POSTAL_CODE = "postal_code";

  private StreetAddressExtractor() {}

  /**
   * O logradouro do endereço, quando o place é específico o bastante para ser uma casa.
   *
   * <p>Exigir o componente {@code route} não vale para o DF. Em São Paulo a rua vem como {@code
   * route}; no DF a casa costuma ser a quadra, e o Google não a rotula de forma estável — na
   * fixture df-taguatinga-qnl5 a quadra veio em {@code route}, e em df-ceilandia a QNM 17 veio só
   * como {@code sublocality_level_3}, sem {@code route} nenhum. São o mesmo tipo de lugar, e a
   * segunda tomava 400 sendo um pin legítimo da praça de lançamento.
   *
   * <p>Então: {@code route} quando existir, senão a subdivisão mais funda — desde que exista alguma
   * <b>abaixo</b> da primeira. O que se recusa não é "place sem route", é place que para na cidade
   * ou na RA/bairro, que aí realmente não é endereço de ninguém.
   */
  public static Optional<String> findStreet(PlaceDetailsResponseDTO details) {
    return findLongText(details, ROUTE).or(() -> findDeepestSubdivision(details));
  }

  /**
   * A subdivisão mais funda, ignorada quando ela é a própria RA/bairro: um place que para em "Águas
   * Claras" ou "Pinheiros" descreve a região inteira, não uma casa dentro dela.
   */
  private static Optional<String> findDeepestSubdivision(PlaceDetailsResponseDTO details) {
    List<LocationComponentDTO> districts =
        AddressComponentClassifier.districtsFromShallowToDeep(
            AddressComponentClassifier.classify(details.addressComponents()));
    if (districts.isEmpty()) {
      return Optional.empty();
    }
    LocationComponentDTO deepest = districts.get(districts.size() - 1);
    return deepest.depth() > SHALLOWEST_DISTRICT_DEPTH
        ? Optional.of(deepest.name())
        : Optional.empty();
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
