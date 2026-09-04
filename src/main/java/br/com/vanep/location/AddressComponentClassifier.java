package br.com.vanep.location;

import br.com.vanep.location.dto.LocationComponentDTO;
import br.com.vanep.location.enums.LocationLevel;
import br.com.vanep.location.exception.UnknownAddressComponentException;
import br.com.vanep.places.dto.AddressComponentDTO;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Traduz {@code addressComponents} do Google em componentes da árvore, conforme a tabela D11
 * decidida na fase 1 com 10 lugares reais.
 *
 * <p>Regra dura: {@code type} presente e fora da tabela é <b>erro</b>, não algo a ignorar. Ignorar
 * silenciosamente é o que transforma "mapeamento errado" em "busca sem resultado" — foi o que
 * tornou o R1 um risco alto.
 */
public final class AddressComponentClassifier {

  private static final Logger log = LoggerFactory.getLogger(AddressComponentClassifier.class);

  /** {@code administrative_area_level_2} é o sinal de cidade — não {@code locality}. Ver D11. */
  public static final String CITY_TYPE = "administrative_area_level_2";

  public static final String CITY_FALLBACK_TYPE = "locality";

  private static final Map<String, LocationLevel> LEVEL_BY_TYPE =
      Map.of(
          "country",
          LocationLevel.COUNTRY,
          "administrative_area_level_1",
          LocationLevel.STATE,
          CITY_TYPE,
          LocationLevel.CITY,
          CITY_FALLBACK_TYPE,
          LocationLevel.CITY,
          "administrative_area_level_4",
          LocationLevel.DISTRICT,
          "sublocality_level_1",
          LocationLevel.DISTRICT,
          "sublocality_level_2",
          LocationLevel.DISTRICT,
          "sublocality_level_3",
          LocationLevel.DISTRICT);

  /** No DF a profundidade 1 vem como {@code adm_4}; na capital de SP, como {@code sub_1}. */
  private static final Map<String, Integer> DISTRICT_DEPTH_BY_TYPE =
      Map.of(
          "administrative_area_level_4", 1,
          "sublocality_level_1", 1,
          "sublocality_level_2", 2,
          "sublocality_level_3", 3);

  /**
   * Tipos de logradouro ou rótulos genéricos que sempre acompanham outro tipo. Não entram na árvore
   * e não são erro.
   */
  private static final Set<String> IGNORED_TYPES =
      Set.of(
          "political",
          "sublocality",
          "route",
          "street_number",
          "postal_code",
          "postal_code_suffix",
          "postal_town",
          "premise",
          "subpremise",
          "plus_code",
          "point_of_interest",
          "establishment",
          "floor",
          "room",
          "intersection");

  private AddressComponentClassifier() {}

  /**
   * @throws UnknownAddressComponentException se algum componente trouxer {@code types} que não caem
   *     nem na tabela D11 nem na lista de ignorados
   */
  public static List<LocationComponentDTO> classify(List<AddressComponentDTO> components) {
    List<LocationComponentDTO> classified = new ArrayList<>();
    for (AddressComponentDTO component : components) {
      if (component.hasNoTypes()) {
        // A fixture df-escola-objetivo tem um componente assim. Falhar aqui derrubaria
        // a resolução de uma escola real — ver a correção do R1.
        log.warn("Address component without types, ignored: {}", component.longText());
        continue;
      }
      classifyOne(component).ifPresent(classified::add);
    }
    return dropRedundantComponents(classified);
  }

  private static Optional<LocationComponentDTO> classifyOne(AddressComponentDTO component) {
    for (String type : component.types()) {
      LocationLevel level = LEVEL_BY_TYPE.get(type);
      if (level != null) {
        return Optional.of(
            new LocationComponentDTO(
                level,
                DISTRICT_DEPTH_BY_TYPE.getOrDefault(type, 0),
                component.longText(),
                component.shortText(),
                type));
      }
    }
    if (component.types().stream().allMatch(IGNORED_TYPES::contains)) {
      return Optional.empty();
    }
    throw new UnknownAddressComponentException(component.longText(), component.types());
  }

  /**
   * A cidade é {@code administrative_area_level_2}; {@code locality} só vale quando aquele falta.
   * Escolher por precedência declarada, e não pela posição no array, importa porque nas cidades do
   * interior os dois aparecem com o mesmo nome.
   */
  public static Optional<LocationComponentDTO> findCity(List<LocationComponentDTO> classified) {
    List<LocationComponentDTO> cities =
        classified.stream().filter(component -> component.level() == LocationLevel.CITY).toList();
    return cities.stream()
        .filter(component -> CITY_TYPE.equals(component.sourceType()))
        .findFirst()
        .or(() -> cities.stream().findFirst());
  }

  public static Optional<LocationComponentDTO> findFirstOfLevel(
      List<LocationComponentDTO> classified, LocationLevel level) {
    if (level == LocationLevel.CITY) {
      return findCity(classified);
    }
    return classified.stream().filter(component -> component.level() == level).findFirst();
  }

  /**
   * Distritos do raso para o fundo, pela profundidade declarada na D11 — nunca pela posição no
   * array. O Google devolve os mesmos níveis em ordens opostas entre chamadas: em uma fixture do DF
   * o {@code sublocality_level_3} vem antes do {@code _2}, em outra depois. Aninhar por posição
   * inverteria a hierarquia em metade dos casos.
   */
  public static List<LocationComponentDTO> districtsFromShallowToDeep(
      List<LocationComponentDTO> classified) {
    return classified.stream()
        .filter(component -> component.level() == LocationLevel.DISTRICT)
        .sorted(Comparator.comparingInt(LocationComponentDTO::depth))
        .toList();
  }

  public static boolean hasDistrictComponent(List<LocationComponentDTO> classified) {
    return classified.stream().anyMatch(component -> component.level() == LocationLevel.DISTRICT);
  }

  /**
   * Remove o que sobra depois da precedência: o {@code locality} redundante quando há {@code
   * administrative_area_level_2}, e o distrito que apenas repete o nome do nó imediatamente acima
   * dele na cadeia.
   *
   * <p>O Google repete o mesmo texto em níveis diferentes em dois padrões distintos, e os dois
   * nascem nas fixtures coletadas na fase 1:
   *
   * <ul>
   *   <li>contra a cidade — em Formosa e Itapetininga o mesmo nome vem em {@code locality}, {@code
   *       administrative_area_level_4} e {@code administrative_area_level_2}. Sem descarte nasceria
   *       um distrito "Formosa" dentro da cidade "Formosa" em toda cidade pequena;
   *   <li>contra o distrito de cima — em {@code destino-nao-escola} "Lago Norte" vem em {@code
   *       administrative_area_level_4} (profundidade 1) <b>e</b> em {@code sublocality_level_2}
   *       (profundidade 2), sob a cidade Brasília. Comparar só com a cidade não pega este: a árvore
   *       viraria Brasília → Lago Norte → Lago Norte → CA 4, o mesmo nome em dois nós.
   * </ul>
   *
   * <p>Uma regra só cobre os dois: descer a cadeia do raso para o fundo e descartar o componente
   * cujo nome normalizado seja igual ao do pai — a cidade, no primeiro nível, e o distrito já
   * aceito, nos demais. Fica o nó mais raso, e o descendente real (CA 4) reancora nele.
   */
  private static List<LocationComponentDTO> dropRedundantComponents(
      List<LocationComponentDTO> classified) {
    Optional<LocationComponentDTO> city = findCity(classified);
    if (city.isEmpty()) {
      return classified;
    }
    List<LocationComponentDTO> kept = new ArrayList<>();
    for (LocationComponentDTO component : classified) {
      if (component.level() == LocationLevel.CITY) {
        if (component == city.get()) {
          kept.add(component);
        }
        continue;
      }
      if (component.level() != LocationLevel.DISTRICT) {
        kept.add(component);
      }
    }

    // Os distritos entram pela profundidade declarada (D11), nunca pela posição no array:
    // é a mesma ordem que o resolver usa para aninhar, então é nela que "o pai" existe.
    String parentName = LocationNameNormalizer.normalize(city.get().name());
    for (LocationComponentDTO district : districtsFromShallowToDeep(classified)) {
      String districtName = LocationNameNormalizer.normalize(district.name());
      if (districtName.equals(parentName)) {
        log.warn(
            "District component named after its parent, dropped: {} (type={}, parent={})",
            district.name(),
            district.sourceType(),
            parentName);
        continue;
      }
      kept.add(district);
      parentName = districtName;
    }
    return kept;
  }
}
