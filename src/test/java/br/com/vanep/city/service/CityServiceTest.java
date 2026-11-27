package br.com.vanep.city.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.city.dto.CityResponseDTO;
import br.com.vanep.city.mapper.CityMapper;
import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.country.model.CountryModel;
import br.com.vanep.shared.error.ApiProblemException;
import br.com.vanep.state.model.StateModel;
import br.com.vanep.state.repository.StateRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class CityServiceTest {
  @Mock private CityRepository cityRepository;
  @Mock private StateRepository stateRepository;
  @Mock private CityMapper mapper;

  private CityService service;

  @BeforeEach
  void setUp() {
    service = new CityService(cityRepository, stateRepository, mapper);
  }

  private StateModel stateSp() {
    CountryModel country = new CountryModel();
    country.setId(100L);
    country.setToken("country-br");
    country.setName("Brasil");

    StateModel state = new StateModel();
    state.setId(1L);
    state.setToken("state-sp");
    state.setName("São Paulo");
    state.setUf("SP");
    state.setCountry(country);
    return state;
  }

  private StateModel stateDf() {
    CountryModel country = new CountryModel();
    country.setId(100L);
    country.setToken("country-br");
    country.setName("Brasil");

    StateModel state = new StateModel();
    state.setId(2L);
    state.setToken("state-df");
    state.setName("Distrito Federal");
    state.setUf("DF");
    state.setCountry(country);
    return state;
  }

  private CityModel cityWithToken(String token) {
    CityModel city = new CityModel();
    city.setToken(token);
    city.setName("Campinas");
    city.setState(stateSp());
    return city;
  }

  private CityResponseDTO responseFor(String token) {
    return new CityResponseDTO(token, "Campinas", "state-sp", "SP", true, null);
  }

  @Test
  void findByUfReturnsPagedResponses() {
    StateModel state = stateSp();
    CityModel city = cityWithToken("abc");
    CityResponseDTO response = responseFor("abc");
    when(stateRepository.findByUf("SP")).thenReturn(Optional.of(state));
    when(cityRepository.findByStateIdAndActiveTrue(eq(1L), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(city)));
    when(mapper.toResponse(city)).thenReturn(response);

    var result = service.findByUf("SP", null, Pageable.unpaged());

    assertThat(result.getContent()).containsExactly(response);
  }

  @Test
  void findByUfUppercasesTheCode() {
    StateModel state = stateDf();
    when(stateRepository.findByUf("DF")).thenReturn(Optional.of(state));
    when(cityRepository.findByStateIdAndActiveTrue(eq(2L), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));

    service.findByUf("df", "  ", Pageable.unpaged());

    verify(stateRepository).findByUf("DF");
  }

  @Test
  void findByUfThrows400WhenUfIsMissing() {
    assertThatThrownBy(() -> service.findByUf(null, null, Pageable.unpaged()))
        .isInstanceOfSatisfying(
            ApiProblemException.class,
            ex -> {
              assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
              assertThat(ex.getCode()).isEqualTo("city.uf.required");
            });
  }

  @Test
  void findByUfThrows404WhenUfIsUnknown() {
    when(stateRepository.findByUf("XX")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.findByUf("XX", null, Pageable.unpaged()))
        .isInstanceOfSatisfying(
            ApiProblemException.class,
            ex -> {
              assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(ex.getCode()).isEqualTo("state.uf.not_found");
            });
  }

  @Test
  void findByUfFiltersByNormalizedNameContaining() {
    StateModel state = stateDf();
    CityModel city = new CityModel();
    city.setToken("bsb");
    city.setName("Brasília");
    city.setState(state);
    CityResponseDTO response = new CityResponseDTO("bsb", "Brasília", "state-df", "DF", true, null);
    when(stateRepository.findByUf("DF")).thenReturn(Optional.of(state));
    when(cityRepository.findByStateIdAndActiveTrueAndNormalizedNameContaining(
            eq(2L), eq("brasilia"), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(city)));
    when(mapper.toResponse(city)).thenReturn(response);

    var result = service.findByUf("DF", "Brasília", Pageable.unpaged());

    assertThat(result.getContent()).containsExactly(response);
  }

  @Test
  void findByTokenReturnsResponse() {
    CityModel city = cityWithToken("tok");
    CityResponseDTO response = responseFor("tok");
    when(cityRepository.findByToken("tok")).thenReturn(Optional.of(city));
    when(mapper.toResponse(city)).thenReturn(response);

    assertThat(service.findByToken("tok")).isEqualTo(response);
  }

  @Test
  void findByTokenThrows404WhenNotFound() {
    when(cityRepository.findByToken("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.findByToken("missing"))
        .isInstanceOfSatisfying(
            ApiProblemException.class,
            ex -> {
              assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(ex.getCode()).isEqualTo("city.not_found");
            });
  }
}
