package br.com.vanep.school.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import br.com.vanep.address.dto.AddressResponseDTO;
import br.com.vanep.address.service.AddressService;
import br.com.vanep.auth.security.RateLimiter;
import br.com.vanep.city.model.CityModel;
import br.com.vanep.location.dto.ResolvedLocationChainDTO;
import br.com.vanep.location.service.LocationResolverService;
import br.com.vanep.places.client.PlacesClient;
import br.com.vanep.places.dto.PlaceDetailsResponseDTO;
import br.com.vanep.school.dto.SchoolResolveRequestDTO;
import br.com.vanep.school.model.SchoolModel;
import br.com.vanep.school.repository.SchoolRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatchers;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.web.server.ResponseStatusException;

class SchoolResolveServiceTest {
  private final PlacesClient places = mock(PlacesClient.class);
  private final LocationResolverService resolver = mock(LocationResolverService.class);
  private final SchoolRepository schools = mock(SchoolRepository.class);
  private final AddressService addresses = mock(AddressService.class);

  private SchoolResolveService serviceWithLimit(int capacity) {
    StaticMessageSource messages = new StaticMessageSource();
    messages.setUseCodeAsDefaultMessage(true);
    return new SchoolResolveService(
        places, resolver, schools, addresses, new RateLimiter(true, capacity, 60), messages);
  }

  private SchoolResolveRequestDTO request(String placeId) {
    return new SchoolResolveRequestDTO(placeId, null);
  }

  private PlaceDetailsResponseDTO school(String placeId) {
    return new PlaceDetailsResponseDTO(
        placeId,
        "Rua X",
        List.of(),
        List.of("school", "educational_institution", "point_of_interest"),
        null);
  }

  @Test
  void rejectsBeyondTheLimitWithoutCallingGoogle() {
    SchoolResolveService service = serviceWithLimit(1);
    given(places.findPlaceDetailsWithName("escola-1", null)).willReturn(school("escola-1"));
    given(schools.findByGooglePlaceId("escola-1")).willReturn(Optional.empty());
    given(resolver.resolveAndPersist(ArgumentMatchers.any()))
        .willThrow(new IllegalStateException("não deveria chegar aqui"));

    assertThatThrownBy(() -> service.resolve("user-1", request("escola-1")))
        .isInstanceOf(IllegalStateException.class);

    assertThatThrownBy(() -> service.resolve("user-1", request("escola-2")))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("429");

    verify(places, never()).findPlaceDetailsWithName("escola-2", null);
  }

  @Test
  void countsTheLimitPerUserNotGlobally() {
    SchoolResolveService service = serviceWithLimit(1);
    given(places.findPlaceDetailsWithName(ArgumentMatchers.anyString(), ArgumentMatchers.any()))
        .willThrow(new IllegalStateException("chamou o Google"));

    assertThatThrownBy(() -> service.resolve("user-1", request("x")))
        .isInstanceOf(IllegalStateException.class);

    assertThatThrownBy(() -> service.resolve("user-2", request("x")))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void refusesAPlaceThatIsNotASchool() {
    SchoolResolveService service = serviceWithLimit(10);
    given(places.findPlaceDetailsWithName("shopping", null))
        .willReturn(
            new PlaceDetailsResponseDTO(
                "shopping",
                "Brasília Shopping",
                List.of(),
                List.of("shopping_mall", "point_of_interest", "establishment"),
                null));

    assertThatThrownBy(() -> service.resolve("user-1", request("shopping")))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("400");

    verifyNoInteractions(resolver);
  }

  @ParameterizedTest
  @ValueSource(strings = {"primary_school", "secondary_school"})
  void acceptsTheSchoolTypesTheAppSearchesBy(String schoolType) {
    SchoolResolveService service = serviceWithLimit(10);
    given(places.findPlaceDetailsWithName("escola", null))
        .willReturn(
            new PlaceDetailsResponseDTO(
                "escola", "Escola X", List.of(), List.of(schoolType, "point_of_interest"), null));
    given(schools.findByGooglePlaceId("escola")).willReturn(Optional.empty());
    given(resolver.resolveAndPersist(ArgumentMatchers.any()))
        .willThrow(new IllegalStateException("passed the school check"));

    assertThatThrownBy(() -> service.resolve("user-1", request("escola")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("passed the school check");
  }

  private SchoolModel knownSchool(Long addressId) {
    SchoolModel school = new SchoolModel();
    school.setId(3L);
    school.setCity(new CityModel());
    school.setAddressId(addressId);
    return school;
  }

  private AddressResponseDTO address(String token) {
    return new AddressResponseDTO(
        token, null, "Rua Estela", "268", null, "Vila Mariana", "c", "São Paulo", "SP", true, null);
  }

  @Test
  void servesAKnownSchoolWithItsAddressWithoutAnyLookup() {
    SchoolResolveService service = serviceWithLimit(10);
    SchoolModel objetivo = knownSchool(40L);
    given(schools.findByGooglePlaceId("objetivo")).willReturn(Optional.of(objetivo));
    given(addresses.toResponseOrNull(40L)).willReturn(address("addr-40"));

    SchoolResolveService.Resolution resolution = service.resolve("user-1", request("objetivo"));

    assertThat(resolution.created()).isFalse();
    assertThat(resolution.school()).isSameAs(objetivo);
    assertThat(resolution.address()).isEqualTo(address("addr-40"));
    verify(places, never()).findPlaceDetailsWithName(ArgumentMatchers.anyString(), any());
    verify(places, never()).findPlaceDetails(ArgumentMatchers.anyString(), any());
    verify(addresses, never()).createForSchoolFromPlace(any(), any());
    verifyNoInteractions(resolver);
  }

  @Test
  void closesTheAutocompleteSessionEvenWhenTheAnswerCameFromTheDatabase() {
    SchoolResolveService service = serviceWithLimit(10);
    given(schools.findByGooglePlaceId("objetivo")).willReturn(Optional.of(knownSchool(40L)));

    service.resolve("user-1", new SchoolResolveRequestDTO("objetivo", "sessao-1"));

    verify(places).closeAutocompleteSession("objetivo", "sessao-1");
    verify(places, never()).findPlaceDetailsWithName(ArgumentMatchers.anyString(), any());
  }

  @Test
  void completesAKnownSchoolWithoutAnAddress() {
    SchoolResolveService service = serviceWithLimit(10);
    SchoolModel objetivo = knownSchool(null);
    PlaceDetailsResponseDTO details = school("objetivo");
    given(schools.findByGooglePlaceId("objetivo")).willReturn(Optional.of(objetivo));
    given(places.findPlaceDetails("objetivo", "sessao-1")).willReturn(details);
    given(addresses.createForSchoolFromPlace(objetivo, details))
        .willReturn(Optional.of(address("addr-new")));

    SchoolResolveService.Resolution resolution =
        service.resolve("user-1", new SchoolResolveRequestDTO("objetivo", "sessao-1"));

    assertThat(resolution.created()).isFalse();
    assertThat(resolution.address()).isEqualTo(address("addr-new"));
    verify(places, never()).closeAutocompleteSession(any(), any());
    verify(places, never()).findPlaceDetailsWithName(ArgumentMatchers.anyString(), any());
    verifyNoInteractions(resolver);
  }

  @Test
  void servesAKnownSchoolWithoutAnAddressAsItIsBeyondTheLimit() {
    SchoolResolveService service = serviceWithLimit(0);
    given(schools.findByGooglePlaceId("objetivo")).willReturn(Optional.of(knownSchool(null)));

    SchoolResolveService.Resolution resolution =
        service.resolve("user-1", new SchoolResolveRequestDTO("objetivo", "sessao-1"));

    assertThat(resolution.address()).isNull();
    verify(places).closeAutocompleteSession("objetivo", "sessao-1");
    verify(places, never()).findPlaceDetails(ArgumentMatchers.anyString(), any());
    verify(addresses, never()).createForSchoolFromPlace(any(), any());
  }

  @Test
  void neverCompletesAKnownSchoolWithoutACity() {
    SchoolResolveService service = serviceWithLimit(10);
    SchoolModel cityless = new SchoolModel();
    given(schools.findByGooglePlaceId("objetivo")).willReturn(Optional.of(cityless));

    assertThat(service.resolve("user-1", request("objetivo")).address()).isNull();
    verify(places, never()).findPlaceDetails(ArgumentMatchers.anyString(), any());
    verify(addresses, never()).createForSchoolFromPlace(any(), any());
  }

  @Test
  void createsTheSchoolAddressFromTheSameLookup() {
    SchoolResolveService service = serviceWithLimit(10);
    PlaceDetailsResponseDTO details = school("bandeirantes");
    CityModel saoPaulo = new CityModel();
    given(schools.findByGooglePlaceId("bandeirantes")).willReturn(Optional.empty());
    given(places.findPlaceDetailsWithName("bandeirantes", null)).willReturn(details);
    given(resolver.resolveAndPersist(details))
        .willReturn(new ResolvedLocationChainDTO(null, null, saoPaulo, List.of(), false));
    given(schools.save(any())).willAnswer(invocation -> invocation.getArgument(0));
    given(addresses.createForSchoolFromPlace(any(), ArgumentMatchers.eq(details)))
        .willReturn(Optional.of(address("addr-new")));

    SchoolResolveService.Resolution resolution = service.resolve("user-1", request("bandeirantes"));

    assertThat(resolution.created()).isTrue();
    assertThat(resolution.school().getCity()).isSameAs(saoPaulo);
    assertThat(resolution.address()).isEqualTo(address("addr-new"));
    verify(places, never()).findPlaceDetails(ArgumentMatchers.anyString(), any());
  }

  @Test
  void createsASchoolWithoutAnAddressWhenThePlaceHasNoStreet() {
    SchoolResolveService service = serviceWithLimit(10);
    PlaceDetailsResponseDTO details = school("sem-rua");
    given(schools.findByGooglePlaceId("sem-rua")).willReturn(Optional.empty());
    given(places.findPlaceDetailsWithName("sem-rua", null)).willReturn(details);
    given(resolver.resolveAndPersist(details))
        .willReturn(new ResolvedLocationChainDTO(null, null, new CityModel(), List.of(), false));
    given(schools.save(any())).willAnswer(invocation -> invocation.getArgument(0));
    given(addresses.createForSchoolFromPlace(any(), ArgumentMatchers.eq(details)))
        .willReturn(Optional.empty());

    SchoolResolveService.Resolution resolution = service.resolve("user-1", request("sem-rua"));

    assertThat(resolution.created()).isTrue();
    assertThat(resolution.address()).isNull();
  }

  @Test
  void fallsBackToFormattedAddressWhenTheMaskBringsNoDisplayName() {
    SchoolResolveService service = serviceWithLimit(10);

    String name =
        service.resolveName(
            new PlaceDetailsResponseDTO("id", "Rua Estela, 268 - Vila Mariana", List.of()));

    assertThat(name).isEqualTo("Rua Estela, 268 - Vila Mariana");
  }

  @Test
  void prefersTheDisplayNameWhenPresent() {
    SchoolResolveService service = serviceWithLimit(10);

    String name =
        service.resolveName(
            new PlaceDetailsResponseDTO(
                "id",
                "Rua Estela, 268",
                List.of(),
                List.of("school"),
                new PlaceDetailsResponseDTO.DisplayName("Colégio Bandeirantes", "pt-BR")));

    assertThat(name).isEqualTo("Colégio Bandeirantes");
    verifyNoInteractions(resolver);
  }
}
