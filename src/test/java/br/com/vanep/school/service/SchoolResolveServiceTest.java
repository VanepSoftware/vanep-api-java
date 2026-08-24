package br.com.vanep.school.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import br.com.vanep.school.repository.SchoolRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.web.server.ResponseStatusException;

/**
 * O limite do R6 é testado aqui, e não no slice: assim o teste escolhe a capacidade em vez de
 * depender do profile, e consegue afirmar o que realmente importa — que o Google <b>não</b> é
 * chamado quando o limite estoura. É o ponto do R6: quem varre {@code placeId}s gasta dinheiro
 * nosso, então a barreira tem de vir antes da chamada paga.
 */
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

  @Test
  void rejectsBeyondTheLimitWithoutCallingGoogle() {
    SchoolResolveService service = serviceWithLimit(1);
    given(places.findPlaceDetailsWithName("escola-1", null))
        .willReturn(new PlaceDetailsResponseDTO("escola-1", "Rua X", List.of()));
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
    // Outro usuário tem balde próprio: o limite protege a fatura, não pune quem
    // não gastou.
    assertThatThrownBy(() -> service.resolve("user-2", request("x")))
        .isInstanceOf(IllegalStateException.class);
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
                new PlaceDetailsResponseDTO.DisplayName("Colégio Bandeirantes", "pt-BR")));

    assertThat(name).isEqualTo("Colégio Bandeirantes");
    verifyNoInteractions(resolver);
  }
}
