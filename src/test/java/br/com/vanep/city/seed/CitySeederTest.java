package br.com.vanep.city.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.state.model.StateModel;
import br.com.vanep.state.repository.StateRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CitySeederTest {

  @Mock private CityRepository cities;
  @Mock private StateRepository states;

  private CitySeeder seeder;

  @BeforeEach
  void setUp() {
    seeder = new CitySeeder(cities, states);
  }

  private StateModel stateWithId(Long id, String uf) {
    StateModel state = new StateModel();
    state.setId(id);
    state.setUf(uf);
    return state;
  }

  @Test
  void createsSeedCitiesWhenMissing() {
    when(states.findByUf("SP")).thenReturn(Optional.of(stateWithId(1L, "SP")));
    when(states.findByUf("RJ")).thenReturn(Optional.of(stateWithId(2L, "RJ")));
    when(cities.existsByNameIgnoreCaseAndStateId(anyString(), anyLong())).thenReturn(false);

    seeder.seed();

    ArgumentCaptor<CityModel> captor = ArgumentCaptor.forClass(CityModel.class);
    verify(cities, times(3)).save(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(city -> city.getName())
        .containsExactly("São Paulo", "Campinas", "Rio de Janeiro");
  }

  @Test
  void skipsCitiesWhenStateMissing() {
    when(states.findByUf(anyString())).thenReturn(Optional.empty());

    seeder.seed();

    verify(cities, never()).save(any(CityModel.class));
  }

  @Test
  void skipsCitiesThatAlreadyExist() {
    when(states.findByUf("SP")).thenReturn(Optional.of(stateWithId(1L, "SP")));
    when(states.findByUf("RJ")).thenReturn(Optional.of(stateWithId(2L, "RJ")));
    when(cities.existsByNameIgnoreCaseAndStateId(anyString(), anyLong())).thenReturn(true);

    seeder.seed();

    verify(cities, never()).save(any(CityModel.class));
  }
}
