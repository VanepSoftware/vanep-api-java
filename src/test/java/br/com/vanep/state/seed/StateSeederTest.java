package br.com.vanep.state.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.country.model.CountryModel;
import br.com.vanep.country.repository.CountryRepository;
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
class StateSeederTest {

  @Mock private StateRepository states;
  @Mock private CountryRepository countries;

  private StateSeeder seeder;

  @BeforeEach
  void setUp() {
    seeder = new StateSeeder(states, countries);
    when(countries.findByName("Brasil")).thenReturn(Optional.of(new CountryModel()));
  }

  @Test
  void createsAll27BrazilianStatesWhenMissing() {
    when(states.existsByUf(anyString())).thenReturn(false);

    seeder.seed();

    ArgumentCaptor<StateModel> captor = ArgumentCaptor.forClass(StateModel.class);
    verify(states, times(27)).save(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(StateModel::getUf)
        .doesNotHaveDuplicates()
        .contains("SP", "RJ", "MG", "DF", "AC", "TO");
  }

  @Test
  void skipsStatesThatAlreadyExist() {
    when(states.existsByUf(anyString())).thenReturn(true);

    seeder.seed();

    verify(states, never()).save(any(StateModel.class));
  }
}
