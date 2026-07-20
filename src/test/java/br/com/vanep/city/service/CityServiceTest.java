package br.com.vanep.city.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.city.dto.CityRequestDTO;
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

  @Test
  void createPersistsCity() {
    StateModel state = stateSp();
    CityModel saved = cityWithToken("tok");
    CityResponseDTO response = responseFor("tok");
    when(stateRepository.findByToken("state-sp")).thenReturn(Optional.of(state));
    when(cityRepository.existsByNameIgnoreCaseAndStateId("Campinas", 1L)).thenReturn(false);
    when(cityRepository.save(any(CityModel.class))).thenReturn(saved);
    when(mapper.toResponse(saved)).thenReturn(response);

    CityResponseDTO result = service.create(new CityRequestDTO("Campinas", "state-sp"));

    assertThat(result).isEqualTo(response);
    verify(cityRepository).save(any(CityModel.class));
  }

  @Test
  void createThrows404WhenStateNotFound() {
    when(stateRepository.findByToken("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.create(new CityRequestDTO("Campinas", "missing")))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
    verify(cityRepository, never()).save(any(CityModel.class));
  }

  @Test
  void createThrows409WhenNameDuplicatedInState() {
    when(stateRepository.findByToken("state-sp")).thenReturn(Optional.of(stateSp()));
    when(cityRepository.existsByNameIgnoreCaseAndStateId("Campinas", 1L)).thenReturn(true);

    assertThatThrownBy(() -> service.create(new CityRequestDTO("Campinas", "state-sp")))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("409");
    verify(cityRepository, never()).save(any(CityModel.class));
  }

  @Test
  void updatePersistsFields() {
    CityModel city = cityWithToken("tok");
    CityResponseDTO response = responseFor("tok");
    when(cityRepository.findByToken("tok")).thenReturn(Optional.of(city));
    when(stateRepository.findByToken("state-sp")).thenReturn(Optional.of(stateSp()));
    when(cityRepository.existsByNameIgnoreCaseAndStateId("Valinhos", 1L)).thenReturn(false);
    when(cityRepository.save(city)).thenReturn(city);
    when(mapper.toResponse(city)).thenReturn(response);

    CityResponseDTO result = service.update("tok", new CityRequestDTO("Valinhos", "state-sp"));

    assertThat(result).isEqualTo(response);
    assertThat(city.getName()).isEqualTo("Valinhos");
  }

  @Test
  void updateKeepsSameNameAndStateWithoutConflict() {
    CityModel city = cityWithToken("tok");
    CityResponseDTO response = responseFor("tok");
    when(cityRepository.findByToken("tok")).thenReturn(Optional.of(city));
    when(stateRepository.findByToken("state-sp")).thenReturn(Optional.of(stateSp()));
    when(cityRepository.save(city)).thenReturn(city);
    when(mapper.toResponse(city)).thenReturn(response);

    CityResponseDTO result = service.update("tok", new CityRequestDTO("Campinas", "state-sp"));

    assertThat(result).isEqualTo(response);
    verify(cityRepository, never()).existsByNameIgnoreCaseAndStateId(any(), any());
  }

  @Test
  void updateThrows409WhenNameBelongsToAnotherCity() {
    CityModel city = cityWithToken("tok");
    when(cityRepository.findByToken("tok")).thenReturn(Optional.of(city));
    when(stateRepository.findByToken("state-sp")).thenReturn(Optional.of(stateSp()));
    when(cityRepository.existsByNameIgnoreCaseAndStateId("Santos", 1L)).thenReturn(true);

    assertThatThrownBy(() -> service.update("tok", new CityRequestDTO("Santos", "state-sp")))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("409");
    verify(cityRepository, never()).save(any(CityModel.class));
  }

  @Test
  void updateThrows404WhenCityNotFound() {
    when(cityRepository.findByToken("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.update("missing", new CityRequestDTO("x", "state-sp")))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }

  @Test
  void updateThrows404WhenStateNotFound() {
    CityModel city = cityWithToken("tok");
    when(cityRepository.findByToken("tok")).thenReturn(Optional.of(city));
    when(stateRepository.findByToken("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.update("tok", new CityRequestDTO("Campinas", "missing")))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }

  @Test
  void deleteSoftDeletesCity() {
    CityModel city = cityWithToken("tok");
    when(cityRepository.findByToken("tok")).thenReturn(Optional.of(city));

    service.delete("tok");

    verify(cityRepository).delete(city);
  }

  @Test
  void deleteThrows404WhenNotFound() {
    when(cityRepository.findByToken("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.delete("missing"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }

  @Test
  void restoreRecoversDeletedCity() {
    CityModel city = cityWithToken("tok");
    CityResponseDTO response = responseFor("tok");
    when(cityRepository.existsDeletedByToken("tok")).thenReturn(true);
    when(cityRepository.findByToken("tok")).thenReturn(Optional.of(city));
    when(mapper.toResponse(city)).thenReturn(response);

    CityResponseDTO result = service.restore("tok");

    assertThat(result).isEqualTo(response);
    verify(cityRepository).restoreByToken("tok");
  }

  @Test
  void restoreThrows409WhenAlreadyActive() {
    CityModel city = cityWithToken("tok");
    when(cityRepository.existsDeletedByToken("tok")).thenReturn(false);
    when(cityRepository.findByToken("tok")).thenReturn(Optional.of(city));

    assertThatThrownBy(() -> service.restore("tok"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("409");
    verify(cityRepository, never()).restoreByToken("tok");
  }

  @Test
  void restoreThrows404WhenNotFound() {
    when(cityRepository.existsDeletedByToken("missing")).thenReturn(false);
    when(cityRepository.findByToken("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.restore("missing"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }
}
