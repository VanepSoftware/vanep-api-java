package br.com.vanep.location.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.country.model.CountryModel;
import br.com.vanep.country.repository.CountryRepository;
import br.com.vanep.district.model.DistrictModel;
import br.com.vanep.district.repository.DistrictRepository;
import br.com.vanep.location.dto.ResolvedLocationChainDTO;
import br.com.vanep.location.exception.UnknownAddressComponentException;
import br.com.vanep.location.exception.UnsupportedCountryException;
import br.com.vanep.places.dto.AddressComponentDTO;
import br.com.vanep.places.dto.PlaceDetailsResponseDTO;
import br.com.vanep.state.repository.StateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class LocationResolverServiceTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Autowired private LocationResolverService resolver;
  @Autowired private CountryRepository countries;
  @Autowired private StateRepository states;
  @Autowired private CityRepository cities;
  @Autowired private DistrictRepository districts;

  @BeforeEach
  void seedCuratedCountry() {
    CountryModel brasil = new CountryModel();
    brasil.setName("Brasil");
    brasil.setIsoCode("BR");
    brasil.setPhoneCode("+55");
    brasil.setCurrency("BRL");
    countries.save(brasil);
  }

  private PlaceDetailsResponseDTO fixture(String name) throws IOException {
    String json =
        new ClassPathResource("fixtures/places/" + name + ".json")
            .getContentAsString(StandardCharsets.UTF_8);
    return MAPPER.readValue(json, PlaceDetailsResponseDTO.class);
  }

  private AddressComponentDTO component(String longText, String shortText, String... types) {
    return new AddressComponentDTO(longText, shortText, List.of(types));
  }

  private PlaceDetailsResponseDTO place(AddressComponentDTO... components) {
    return new PlaceDetailsResponseDTO("place-id", "endereço", List.of(components));
  }

  private PlaceDetailsResponseDTO taguatingaOnly() {
    return place(
        component("Brazil", "BR", "country", "political"),
        component("Distrito Federal", "DF", "administrative_area_level_1", "political"),
        component("Brasília", "Brasília", "administrative_area_level_2", "political"),
        component("Taguatinga", "Taguatinga", "administrative_area_level_4", "political"));
  }

  // --- resolveAndPersist ---------------------------------------------------

  @Test
  void createsTheWholeChainOnFirstResolution() throws IOException {
    ResolvedLocationChainDTO chain = resolver.resolveAndPersist(fixture("df-taguatinga-qnl5"));

    assertThat(chain.country().getIsoCode()).isEqualTo("BR");
    assertThat(chain.state().getUf()).isEqualTo("DF");
    assertThat(chain.city().getName()).isEqualTo("Brasília");
    assertThat(chain.districts())
        .extracting(DistrictModel::getName)
        .containsExactly("Taguatinga", "Setor L Norte", "QNL 5");
  }

  @Test
  void nestsDistrictsByDeclaredDepth() throws IOException {
    ResolvedLocationChainDTO chain = resolver.resolveAndPersist(fixture("df-taguatinga-qnl5"));

    List<DistrictModel> chainNodes = chain.districts();
    assertThat(chainNodes.get(0).getParent()).isNull();
    assertThat(chainNodes.get(1).getParent().getId()).isEqualTo(chainNodes.get(0).getId());
    assertThat(chainNodes.get(2).getParent().getId()).isEqualTo(chainNodes.get(1).getId());
  }

  @Test
  void isIdempotentAcrossRepeatedResolutions() throws IOException {
    ResolvedLocationChainDTO first = resolver.resolveAndPersist(fixture("df-taguatinga-qnl5"));
    ResolvedLocationChainDTO second = resolver.resolveAndPersist(fixture("df-taguatinga-qnl5"));

    assertThat(second.city().getId()).isEqualTo(first.city().getId());
    assertThat(second.districts())
        .extracting(DistrictModel::getId)
        .isEqualTo(first.districts().stream().map(DistrictModel::getId).toList());
    assertThat(districts.count()).isEqualTo(3);
    assertThat(cities.count()).isEqualTo(1);
  }

  @Test
  void reusesTheExistingBranchWhenAnotherPlaceSharesIt() throws IOException {
    resolver.resolveAndPersist(fixture("df-taguatinga-qnl5"));
    ResolvedLocationChainDTO other = resolver.resolveAndPersist(fixture("df-escola-objetivo"));

    assertThat(other.districts().get(0).getName()).isEqualTo("Taguatinga");
    // Taguatinga é compartilhada; só Taguatinga Norte e QI 21 nascem.
    assertThat(districts.count()).isEqualTo(5);
    assertThat(cities.count()).isEqualTo(1);
  }

  @Test
  void matchesCountryByIsoCodeNotByEnglishName() throws IOException {
    ResolvedLocationChainDTO chain = resolver.resolveAndPersist(fixture("df-ceilandia"));

    assertThat(chain.country().getName()).isEqualTo("Brasil");
    assertThat(chain.country().getIsoCode()).isEqualTo("BR");
  }

  @Test
  void rejectsAPlaceFromAnUnsupportedCountry() {
    PlaceDetailsResponseDTO abroad =
        place(
            component("United States", "US", "country", "political"),
            component("California", "CA", "administrative_area_level_1", "political"),
            component(
                "San Francisco", "San Francisco", "administrative_area_level_2", "political"));

    assertThatThrownBy(() -> resolver.resolveAndPersist(abroad))
        .isInstanceOf(UnsupportedCountryException.class);
    assertThat(states.count()).isZero();
    assertThat(cities.count()).isZero();
  }

  @Test
  void appliesTheCuratedDistrictPolicyToLazilyCreatedStates() throws IOException {
    resolver.resolveAndPersist(fixture("df-taguatinga-qnl5"));
    resolver.resolveAndPersist(fixture("interior-formosa-go"));

    assertThat(states.findByUf("DF")).get().extracting("requiresDistrict").isEqualTo(true);
    assertThat(states.findByUf("GO")).get().extracting("requiresDistrict").isEqualTo(false);
  }

  // --- D2: o nó vem dos componentes, nunca do place escolhido --------------

  @Test
  void anchorsOnTheComponentChainAndIgnoresThePlaceLabel() {
    PlaceDetailsResponseDTO chosenTaguatingaNorte =
        new PlaceDetailsResponseDTO(
            "place-taguatinga-norte",
            "Taguatinga Norte, Brasília - DF",
            taguatingaOnly().addressComponents());

    ResolvedLocationChainDTO chain = resolver.resolveAndPersist(chosenTaguatingaNorte);

    assertThat(chain.districts()).extracting(DistrictModel::getName).containsExactly("Taguatinga");
    assertThat(districts.findAll())
        .extracting(DistrictModel::getName)
        .doesNotContain("Taguatinga Norte");
  }

  // --- resolveAnchor: leitura pura ----------------------------------------

  @Test
  void anchorStopsAtTheDeepestExistingNodeWithoutWriting() throws IOException {
    resolver.resolveAndPersist(taguatingaOnly());
    long districtsBefore = districts.count();

    ResolvedLocationChainDTO anchor =
        resolver.resolveAnchor(fixture("df-taguatinga-qnl5")).orElseThrow();

    assertThat(anchor.districts()).extracting(DistrictModel::getName).containsExactly("Taguatinga");
    assertThat(anchor.deepestDistrict())
        .get()
        .extracting(DistrictModel::getName)
        .isEqualTo("Taguatinga");
    assertThat(districts.count()).isEqualTo(districtsBefore);
  }

  @Test
  void anchorCreatesNothingWhenTheCityIsUnknown() throws IOException {
    long before = cities.count() + districts.count() + states.count();

    assertThat(resolver.resolveAnchor(fixture("df-taguatinga-qnl5"))).isEmpty();

    assertThat(cities.count() + districts.count() + states.count()).isEqualTo(before);
  }

  @Test
  void anchorReportsThatThePlaceCarriedDistrictComponentsTheTreeDoesNotHave() throws IOException {
    // Cidade existe, nenhum distrito existe.
    resolver.resolveAndPersist(
        place(
            component("Brazil", "BR", "country", "political"),
            component("Distrito Federal", "DF", "administrative_area_level_1", "political"),
            component("Brasília", "Brasília", "administrative_area_level_2", "political")));

    ResolvedLocationChainDTO anchor =
        resolver.resolveAnchor(fixture("df-taguatinga-qnl5")).orElseThrow();

    assertThat(anchor.districts()).isEmpty();
    assertThat(anchor.hasDistrictComponent()).isTrue();
    assertThat(anchor.anchoredAboveTheDistrictComponents()).isTrue();
  }

  @Test
  void anchorMatchesTheSameNodeThatAWriteWouldHaveProduced() throws IOException {
    ResolvedLocationChainDTO written = resolver.resolveAndPersist(fixture("df-taguatinga-qnl5"));
    ResolvedLocationChainDTO anchored =
        resolver.resolveAnchor(fixture("df-taguatinga-qnl5")).orElseThrow();

    assertThat(anchored.deepestDistrict().orElseThrow().getId())
        .isEqualTo(written.deepestDistrict().orElseThrow().getId());
  }

  // --- ancestrais ----------------------------------------------------------

  @Test
  void listsAncestorsFromDeepestToShallowest() throws IOException {
    ResolvedLocationChainDTO chain = resolver.resolveAndPersist(fixture("df-taguatinga-qnl5"));

    assertThat(resolver.findAncestors(chain.deepestDistrict().orElseThrow()))
        .extracting(DistrictModel::getName)
        .containsExactly("QNL 5", "Setor L Norte", "Taguatinga");
  }

  @Test
  void ancestorsOfADirectChildOfTheCityAreOnlyItself() {
    ResolvedLocationChainDTO chain = resolver.resolveAndPersist(taguatingaOnly());

    assertThat(resolver.findAncestors(chain.deepestDistrict().orElseThrow()))
        .extracting(DistrictModel::getName)
        .containsExactly("Taguatinga");
  }

  // --- R1: falhar alto -----------------------------------------------------

  @Test
  void failsLoudOnUnmappedTypeAndPersistsNothing() {
    PlaceDetailsResponseDTO withUnknownType =
        place(
            component("Brazil", "BR", "country", "political"),
            component("Distrito Federal", "DF", "administrative_area_level_1", "political"),
            component("Brasília", "Brasília", "administrative_area_level_2", "political"),
            component("Zona Rural", "Zona Rural", "colloquial_area"));

    assertThatThrownBy(() -> resolver.resolveAndPersist(withUnknownType))
        .isInstanceOf(UnknownAddressComponentException.class);

    assertThat(states.count()).isZero();
    assertThat(cities.count()).isZero();
    assertThat(districts.count()).isZero();
  }

  @Test
  void doesNotCreateADistrictNamedAfterItsOwnCity() throws IOException {
    ResolvedLocationChainDTO chain = resolver.resolveAndPersist(fixture("interior-formosa-go"));

    assertThat(chain.city().getName()).isEqualTo("Formosa");
    assertThat(chain.districts()).isEmpty();
    assertThat(chain.hasDistrictComponent()).isFalse();
    assertThat(districts.count()).isZero();
  }

  @Test
  void resolvesASaoPauloNeighbourhoodAsADirectChildOfTheCity() throws IOException {
    ResolvedLocationChainDTO chain = resolver.resolveAndPersist(fixture("sp-capital-pinheiros"));

    assertThat(chain.city().getName()).isEqualTo("São Paulo");
    assertThat(chain.districts()).extracting(DistrictModel::getName).containsExactly("Pinheiros");
    assertThat(chain.districts().get(0).getParent()).isNull();
  }

  @Test
  void keepsCitiesOfDifferentStatesApart() throws IOException {
    resolver.resolveAndPersist(fixture("sp-capital-pinheiros"));
    resolver.resolveAndPersist(fixture("interior-itapetininga"));

    assertThat(cities.count()).isEqualTo(2);
    CityModel saoPaulo = cities.findFirstByNameIgnoreCase("São Paulo").orElseThrow();
    assertThat(saoPaulo.getState().getUf()).isEqualTo("SP");
  }
}
