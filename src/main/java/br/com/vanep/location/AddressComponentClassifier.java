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

public final class AddressComponentClassifier {
  private static final Logger log = LoggerFactory.getLogger(AddressComponentClassifier.class);

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

  private static final Map<String, Integer> DISTRICT_DEPTH_BY_TYPE =
      Map.of(
          "administrative_area_level_4", 1,
          "sublocality_level_1", 1,
          "sublocality_level_2", 2,
          "sublocality_level_3", 3);

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

  public static List<LocationComponentDTO> classify(List<AddressComponentDTO> components) {
    List<LocationComponentDTO> classified = new ArrayList<>();
    for (AddressComponentDTO component : components) {
      if (component.hasNoTypes()) {
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

  public static List<LocationComponentDTO> districtsFromShallowToDeep(
      List<LocationComponentDTO> classified) {
    return classified.stream()
        .filter(component -> component.level() == LocationLevel.DISTRICT)
        .sorted(Comparator.comparingInt(component -> component.depth()))
        .toList();
  }

  public static boolean hasDistrictComponent(List<LocationComponentDTO> classified) {
    return classified.stream().anyMatch(component -> component.level() == LocationLevel.DISTRICT);
  }

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
