package br.com.vanep.city.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.state.model.StateModel;
import br.com.vanep.state.repository.StateRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;

@ExtendWith(MockitoExtension.class)
class CitySeederTest {
  @Mock private CityRepository cities;
  @Mock private StateRepository states;

  private CitySeeder seeder;
  private StateModel distritoFederal;
  private StateModel goias;
  private StateModel matoGrosso;

  @BeforeEach
  void setUp() {
    seeder =
        new CitySeeder(cities, states, new ClassPathResource("fixtures/ibge-municipalities.json"));

    distritoFederal = new StateModel();
    distritoFederal.setUf("DF");
    distritoFederal.setName("Distrito Federal");

    goias = new StateModel();
    goias.setUf("GO");
    goias.setName("Goiás");

    matoGrosso = new StateModel();
    matoGrosso.setUf("MT");
    matoGrosso.setName("Mato Grosso");
  }

  @Test
  void createsBrasiliaWithIbgeCodeUnderExistingDf() {
    stubEmptyCatalogAndCuratedStates();

    seeder.seed();

    ArgumentCaptor<CityModel> captor = ArgumentCaptor.forClass(CityModel.class);
    verify(cities, times(3)).save(captor.capture());

    CityModel brasilia =
        captor.getAllValues().stream()
            .filter(city -> "5300108".equals(city.getIbgeCode()))
            .findFirst()
            .orElseThrow();
    assertThat(brasilia.getName()).isEqualTo("Brasília");
    assertThat(brasilia.getState()).isEqualTo(distritoFederal);
    assertThat(brasilia.getRequiresDistrict()).isNull();
  }

  @Test
  void mapsCristalinaToExistingGoAndIgnoresStatisticalCuts() {
    stubEmptyCatalogAndCuratedStates();

    seeder.seed();

    ArgumentCaptor<CityModel> captor = ArgumentCaptor.forClass(CityModel.class);
    verify(cities, times(3)).save(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(city -> city.getName())
        .containsExactlyInAnyOrder("Brasília", "Cristalina", "Boa Esperança do Norte")
        .doesNotContain("Entorno de Brasília", "Luziânia", "Sorriso", "Sinop");
    assertThat(captor.getAllValues())
        .filteredOn(city -> "5206206".equals(city.getIbgeCode()))
        .extracting(city -> city.getState().getUf())
        .containsExactly("GO");
  }

  @Test
  void secondRunDoesNotDuplicateCities() {
    Map<String, CityModel> stored = new HashMap<>();
    when(cities.findByIbgeCode(anyString()))
        .thenAnswer(invocation -> Optional.ofNullable(stored.get(invocation.getArgument(0))));
    when(states.findByUf("DF")).thenReturn(Optional.of(distritoFederal));
    when(states.findByUf("GO")).thenReturn(Optional.of(goias));
    when(states.findByUf("MT")).thenReturn(Optional.of(matoGrosso));
    when(cities.save(any(CityModel.class)))
        .thenAnswer(
            invocation -> {
              CityModel city = invocation.getArgument(0);
              stored.put(city.getIbgeCode(), city);
              return city;
            });

    seeder.seed();
    seeder.seed();

    verify(cities, times(3)).save(any(CityModel.class));
    assertThat(stored).hasSize(3).containsKeys("5300108", "5206206", "5101837");
  }

  @Test
  void seedsMunicipalityFromImediataUfWhenMicrorregiaoIsNull() {
    stubEmptyCatalogAndCuratedStates();

    seeder.seed();

    ArgumentCaptor<CityModel> captor = ArgumentCaptor.forClass(CityModel.class);
    verify(cities, times(3)).save(captor.capture());
    CityModel boaEsperanca =
        captor.getAllValues().stream()
            .filter(city -> "5101837".equals(city.getIbgeCode()))
            .findFirst()
            .orElseThrow();
    assertThat(boaEsperanca.getName()).isEqualTo("Boa Esperança do Norte");
    assertThat(boaEsperanca.getState()).isEqualTo(matoGrosso);
    verify(states).findByUf("MT");
  }

  @Test
  void skipsMunicipalityWhenUfCannotBeResolvedAfterFallback() {
    stubEmptyCatalogAndCuratedStates();

    seeder.seed();

    ArgumentCaptor<CityModel> captor = ArgumentCaptor.forClass(CityModel.class);
    verify(cities, times(3)).save(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(city -> city.getIbgeCode())
        .containsExactlyInAnyOrder("5300108", "5206206", "5101837")
        .doesNotContain("9999999");
  }

  @Test
  void rejectsMunicipalityWhenUfIsNotCurated() {
    when(cities.findByIbgeCode(anyString())).thenReturn(Optional.empty());
    when(states.findByUf("GO")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> seeder.seed())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("GO");
    verify(cities, never()).save(any(CityModel.class));
  }

  @Test
  void rejectsMissingDump() {
    CitySeeder missingDump =
        new CitySeeder(
            cities, states, new ClassPathResource("fixtures/missing-ibge-municipalities.json"));

    assertThatThrownBy(missingDump::seed).isInstanceOf(IllegalStateException.class);
  }

  private void stubEmptyCatalogAndCuratedStates() {
    when(cities.findByIbgeCode(anyString())).thenReturn(Optional.empty());
    when(states.findByUf("DF")).thenReturn(Optional.of(distritoFederal));
    when(states.findByUf("GO")).thenReturn(Optional.of(goias));
    when(states.findByUf("MT")).thenReturn(Optional.of(matoGrosso));
    when(cities.save(any(CityModel.class))).thenAnswer(invocation -> invocation.getArgument(0));
  }
}
