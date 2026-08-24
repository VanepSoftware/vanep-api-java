package br.com.vanep.city.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import br.com.vanep.city.dto.CityResponseDTO;
import br.com.vanep.city.mapper.CityMapper;
import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.country.model.CountryModel;
import br.com.vanep.state.model.StateModel;
import br.com.vanep.state.repository.StateRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class CityServiceTest {

  @Mock private CityRepository cityRepository;
  @Mock private StateRepository stateRepository;
  @Mock private CityMapper mapper;
  @Mock private MessageSource messages;

  private CityService service;

  @BeforeEach
  void setUp() {
    service = new CityService(cityRepository, stateRepository, mapper, messages);
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
  void findAllReturnsPagedResponses() {
    CityModel city = cityWithToken("abc");
    CityResponseDTO response = responseFor("abc");
    when(cityRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(city)));
    when(mapper.toResponse(city)).thenReturn(response);

    var result = service.findAll(Pageable.unpaged());

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
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }
}
