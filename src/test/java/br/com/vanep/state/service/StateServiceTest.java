package br.com.vanep.state.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import br.com.vanep.country.model.CountryModel;
import br.com.vanep.state.dto.StateResponseDTO;
import br.com.vanep.state.mapper.StateMapper;
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
class StateServiceTest {

  @Mock private StateRepository repository;
  @Mock private StateMapper mapper;
  @Mock private MessageSource messages;

  private StateService service;

  @BeforeEach
  void setUp() {
    service = new StateService(repository, mapper, messages);
  }

  private StateModel stateWithToken(String token) {
    CountryModel country = new CountryModel();
    country.setId(100L);
    country.setName("Brasil");

    StateModel state = new StateModel();
    state.setToken(token);
    state.setName("São Paulo");
    state.setUf("SP");
    state.setCountry(country);
    return state;
  }

  @Test
  void findAllReturnsPagedResponses() {
    StateModel state = stateWithToken("abc");
    StateResponseDTO response = new StateResponseDTO("abc", "São Paulo", "SP", true);
    when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(state)));
    when(mapper.toResponse(state)).thenReturn(response);

    var result = service.findAll(Pageable.unpaged());

    assertThat(result.getContent()).containsExactly(response);
  }

  @Test
  void findByTokenReturnsResponse() {
    StateModel state = stateWithToken("tok");
    StateResponseDTO response = new StateResponseDTO("tok", "São Paulo", "SP", true);
    when(repository.findByToken("tok")).thenReturn(Optional.of(state));
    when(mapper.toResponse(state)).thenReturn(response);

    assertThat(service.findByToken("tok")).isEqualTo(response);
  }

  @Test
  void findByTokenThrows404WhenNotFound() {
    when(repository.findByToken("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.findByToken("missing"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }
}
