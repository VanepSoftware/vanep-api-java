package br.com.vanep.location;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.vanep.location.dto.LocationComponentDTO;
import br.com.vanep.location.enums.LocationLevel;
import br.com.vanep.location.exception.UnknownAddressComponentException;
import br.com.vanep.places.dto.AddressComponentDTO;
import br.com.vanep.places.dto.PlaceDetailsResponseDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class AddressComponentClassifierTest {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private List<LocationComponentDTO> classifyFixture(String name) throws IOException {
    String json =
        new ClassPathResource("fixtures/places/" + name + ".json")
            .getContentAsString(StandardCharsets.UTF_8);
    PlaceDetailsResponseDTO details = MAPPER.readValue(json, PlaceDetailsResponseDTO.class);
    return AddressComponentClassifier.classify(details.addressComponents());
  }

  private String nameOfLevel(List<LocationComponentDTO> components, LocationLevel level) {
    return AddressComponentClassifier.findFirstOfLevel(components, level)
        .map(component -> component.name())
        .orElse(null);
  }

  @Test
  void everyCollectedFixtureClassifiesWithoutUnknownTypes() throws IOException {
    for (String fixture :
        List.of(
            "df-taguatinga-qnl5",
            "df-aguas-claras",
            "df-ceilandia",
            "df-escola-objetivo",
            "sp-capital-pinheiros",
            "sp-capital-tatuape",
            "sp-escola-bandeirantes",
            "interior-itapetininga",
            "interior-formosa-go",
            "destino-nao-escola")) {
      assertThat(classifyFixture(fixture)).as(fixture).isNotEmpty();
    }
  }

  @Test
  void readsCityFromAdministrativeAreaLevel2NotFromLocality() throws IOException {
    List<LocationComponentDTO> components = classifyFixture("df-taguatinga-qnl5");

    assertThat(nameOfLevel(components, LocationLevel.CITY)).isEqualTo("Brasília");
    assertThat(AddressComponentClassifier.findCity(components))
        .get()
        .extracting(component -> component.sourceType())
        .isEqualTo(AddressComponentClassifier.CITY_TYPE);
  }

  @Test
  void prefersAdministrativeAreaLevel2WhenLocalityAlsoPresent() throws IOException {
    List<LocationComponentDTO> components = classifyFixture("interior-formosa-go");

    assertThat(AddressComponentClassifier.findCity(components))
        .get()
        .extracting(component -> component.sourceType())
        .isEqualTo(AddressComponentClassifier.CITY_TYPE);
    assertThat(components).filteredOn(c -> c.level() == LocationLevel.CITY).hasSize(1);
  }

  @Test
  void dropsDistrictThatRepeatsTheCityNameInTheInterior() throws IOException {
    List<LocationComponentDTO> formosa = classifyFixture("interior-formosa-go");
    List<LocationComponentDTO> itapetininga = classifyFixture("interior-itapetininga");

    assertThat(formosa).noneMatch(c -> c.level() == LocationLevel.DISTRICT);
    assertThat(itapetininga).noneMatch(c -> c.level() == LocationLevel.DISTRICT);
  }

  @Test
  void mapsDistritoFederalRegionAsDepthOneDistrict() throws IOException {
    List<LocationComponentDTO> components = classifyFixture("df-aguas-claras");

    assertThat(AddressComponentClassifier.districtsFromShallowToDeep(components))
        .singleElement()
        .satisfies(
            district -> {
              assertThat(district.name()).isEqualTo("Águas Claras");
              assertThat(district.depth()).isEqualTo(1);
              assertThat(district.sourceType()).isEqualTo("administrative_area_level_4");
            });
  }

  @Test
  void mapsSaoPauloNeighbourhoodAsDepthOneDistrict() throws IOException {
    List<LocationComponentDTO> components = classifyFixture("sp-capital-pinheiros");

    assertThat(nameOfLevel(components, LocationLevel.CITY)).isEqualTo("São Paulo");
    assertThat(AddressComponentClassifier.districtsFromShallowToDeep(components))
        .singleElement()
        .satisfies(
            district -> {
              assertThat(district.name()).isEqualTo("Pinheiros");
              assertThat(district.depth()).isEqualTo(1);
              assertThat(district.sourceType()).isEqualTo("sublocality_level_1");
            });
  }

  @Test
  void dropsDistrictThatOnlyRepeatsItsParentName() throws IOException {
    assertThat(
            AddressComponentClassifier.districtsFromShallowToDeep(
                classifyFixture("destino-nao-escola")))
        .extracting(component -> component.name())
        .containsExactly("Lago Norte", "CA 4");
  }

  @Test
  void dropsDistrictNamedAfterItsCity() throws IOException {
    assertThat(
            AddressComponentClassifier.districtsFromShallowToDeep(
                classifyFixture("interior-formosa-go")))
        .isEmpty();
  }

  @Test
  void ordersDistrictsByDeclaredDepthNotByArrayPosition() throws IOException {
    assertThat(
            AddressComponentClassifier.districtsFromShallowToDeep(
                classifyFixture("df-taguatinga-qnl5")))
        .extracting(component -> component.name())
        .containsExactly("Taguatinga", "Setor L Norte", "QNL 5");

    assertThat(
            AddressComponentClassifier.districtsFromShallowToDeep(
                classifyFixture("df-escola-objetivo")))
        .extracting(component -> component.name())
        .containsExactly("Taguatinga", "Taguatinga Norte", "QI 21");
  }

  @Test
  void ignoresComponentWithoutTypesInsteadOfRejectingTheWholePlace() throws IOException {
    List<LocationComponentDTO> components = classifyFixture("df-escola-objetivo");

    assertThat(nameOfLevel(components, LocationLevel.CITY)).isEqualTo("Brasília");
    assertThat(components).noneMatch(component -> component.name().startsWith("Q1 21 LOTE"));
  }

  @Test
  void ignoresStreetLevelComponents() throws IOException {
    List<LocationComponentDTO> components = classifyFixture("sp-escola-bandeirantes");

    assertThat(components)
        .extracting(component -> component.name())
        .doesNotContain("268", "Rua Estela", "04011-001");
  }

  @Test
  void failsLoudOnATypeOutsideTheDecidedTable() {
    List<AddressComponentDTO> components =
        List.of(
            new AddressComponentDTO("Brazil", "BR", List.of("country", "political")),
            new AddressComponentDTO("Algum Bairro", "Algum Bairro", List.of("neighborhood")));

    assertThatThrownBy(() -> AddressComponentClassifier.classify(components))
        .isInstanceOf(UnknownAddressComponentException.class)
        .hasMessageContaining("Algum Bairro");
  }

  @Test
  void reportsTheOffendingTypesSoThePlazaGapIsDiagnosable() {
    List<AddressComponentDTO> components =
        List.of(new AddressComponentDTO("Zona Rural", "Zona Rural", List.of("colloquial_area")));

    assertThatThrownBy(() -> AddressComponentClassifier.classify(components))
        .isInstanceOf(UnknownAddressComponentException.class)
        .extracting(ex -> ((UnknownAddressComponentException) ex).getTypes())
        .isEqualTo(List.of("colloquial_area"));
  }

  @Test
  void countryIsMatchedByIsoCodeBecauseTheLongTextComesInEnglish() throws IOException {
    List<LocationComponentDTO> components = classifyFixture("df-ceilandia");

    assertThat(AddressComponentClassifier.findFirstOfLevel(components, LocationLevel.COUNTRY))
        .get()
        .satisfies(
            country -> {
              assertThat(country.name()).isEqualTo("Brazil");
              assertThat(country.shortName()).isEqualTo("BR");
            });
  }

  @Test
  void stateShortNameIsTheUf() throws IOException {
    List<LocationComponentDTO> components = classifyFixture("interior-formosa-go");

    assertThat(AddressComponentClassifier.findFirstOfLevel(components, LocationLevel.STATE))
        .get()
        .satisfies(
            state -> {
              assertThat(state.name()).isEqualTo("State of Goiás");
              assertThat(state.shortName()).isEqualTo("GO");
            });
  }

  @Test
  void reportsWhetherThePlaceCarriesADistrictComponent() throws IOException {
    assertThat(AddressComponentClassifier.hasDistrictComponent(classifyFixture("df-aguas-claras")))
        .isTrue();
    assertThat(
            AddressComponentClassifier.hasDistrictComponent(classifyFixture("interior-formosa-go")))
        .isFalse();
  }
}
