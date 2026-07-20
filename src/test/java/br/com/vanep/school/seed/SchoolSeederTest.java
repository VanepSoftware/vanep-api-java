package br.com.vanep.school.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.school.model.SchoolModel;
import br.com.vanep.school.repository.SchoolRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SchoolSeederTest {

  @Mock private SchoolRepository schools;

  private SchoolSeeder seeder;

  @BeforeEach
  void setUp() {
    seeder = new SchoolSeeder(schools);
  }

  @Test
  void createsTwoSchoolsWhenMissing() {
    when(schools.existsByCnpj(anyString())).thenReturn(false);

    seeder.seed();

    ArgumentCaptor<SchoolModel> captor = ArgumentCaptor.forClass(SchoolModel.class);
    verify(schools, times(2)).save(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(school -> school.getName())
        .containsExactly("Escola Municipal Monteiro Lobato", "Colégio Santa Clara");
  }

  @Test
  void skipsSchoolsThatAlreadyExist() {
    when(schools.existsByCnpj(anyString())).thenReturn(true);

    seeder.seed();

    verify(schools, never()).save(any(SchoolModel.class));
  }
}
