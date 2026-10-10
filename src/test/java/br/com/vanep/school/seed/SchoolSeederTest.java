package br.com.vanep.school.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.school.model.SchoolModel;
import br.com.vanep.school.repository.SchoolRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SchoolSeederTest {

  @Mock private SchoolRepository schools;
  @Mock private CityRepository cities;

  private SchoolSeeder seeder;

  @BeforeEach
  void setUp() {
    seeder = new SchoolSeeder(schools, cities);
  }

  @Test
  void createsTheSeedSchoolInTheSeedCity() {
    CityModel city = new CityModel();
    when(schools.findFirstByName(SchoolSeeder.SEED_SCHOOL_NAME)).thenReturn(Optional.empty());
    when(cities.findByIbgeCode(SchoolSeeder.SEED_CITY_IBGE_CODE)).thenReturn(Optional.of(city));

    seeder.seed();

    ArgumentCaptor<SchoolModel> captor = ArgumentCaptor.forClass(SchoolModel.class);
    verify(schools).save(captor.capture());
    assertThat(captor.getValue().getName()).isEqualTo(SchoolSeeder.SEED_SCHOOL_NAME);
    assertThat(captor.getValue().getCity()).isSameAs(city);
  }

  @Test
  void isIdempotentWhenTheSeedSchoolAlreadyExists() {
    when(schools.findFirstByName(SchoolSeeder.SEED_SCHOOL_NAME))
        .thenReturn(Optional.of(new SchoolModel()));

    seeder.seed();

    verify(schools, never()).save(any());
  }

  @Test
  void skipsWhenTheSeedCityIsMissing() {
    when(schools.findFirstByName(SchoolSeeder.SEED_SCHOOL_NAME)).thenReturn(Optional.empty());
    when(cities.findByIbgeCode(SchoolSeeder.SEED_CITY_IBGE_CODE)).thenReturn(Optional.empty());

    seeder.seed();

    verify(schools, never()).save(any());
  }
}
