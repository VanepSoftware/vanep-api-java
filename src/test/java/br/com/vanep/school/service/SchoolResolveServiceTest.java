package br.com.vanep.school.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import br.com.vanep.auth.security.RateLimiter;
import br.com.vanep.location.service.LocationResolverService;
import br.com.vanep.places.client.PlacesClient;
import br.com.vanep.places.dto.PlaceDetailsResponseDTO;
import br.com.vanep.school.dto.SchoolResolveRequestDTO;
import br.com.vanep.school.model.SchoolModel;
import br.com.vanep.school.repository.SchoolRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.web.server.ResponseStatusException;

class SchoolResolveServiceTest {
  private final PlacesClient places = mock(PlacesClient.class);
  private final LocationResolverService resolver = mock(LocationResolverService.class);
  private final SchoolRepository schools = mock(SchoolRepository.class);

  private SchoolResolveService serviceWithLimit(int capacity) {
    StaticMessageSource messages = new StaticMessageSource();
    messages.setUseCodeAsDefaultMessage(true);
    return new SchoolResolveService(
        places, resolver, schools, new RateLimiter(true, capacity, 60), messages);
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

  @Test
  void servesAKnownSchoolWithoutPayingForTheProLookup() {
    SchoolResolveService service = serviceWithLimit(10);
    SchoolModel objetivo = new SchoolModel();
    given(schools.findByGooglePlaceId("objetivo")).willReturn(Optional.of(objetivo));

    SchoolResolveService.Resolution resolution = service.resolve("user-1", request("objetivo"));

    assertThat(resolution.created()).isFalse();
    assertThat(resolution.school()).isSameAs(objetivo);
    verify(places, never()).findPlaceDetailsWithName(ArgumentMatchers.anyString(), any());
    verifyNoInteractions(resolver);
  }

  @Test
  void closesTheAutocompleteSessionEvenWhenTheAnswerCameFromTheDatabase() {
    SchoolResolveService service = serviceWithLimit(10);
    given(schools.findByGooglePlaceId("objetivo")).willReturn(Optional.of(new SchoolModel()));

    service.resolve("user-1", new SchoolResolveRequestDTO("objetivo", "sessao-1"));

    verify(places).closeAutocompleteSession("objetivo", "sessao-1");
    verify(places, never()).findPlaceDetailsWithName(ArgumentMatchers.anyString(), any());
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
