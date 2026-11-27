package br.com.vanep.country.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.country.dto.CountryRequestDTO;
import br.com.vanep.country.dto.CountryResponseDTO;
import br.com.vanep.country.mapper.CountryMapper;
import br.com.vanep.country.model.CountryModel;
import br.com.vanep.country.repository.CountryRepository;
import br.com.vanep.shared.error.ApiProblemException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class CountryServiceTest {

  @Mock private CountryRepository repository;

  private CountryService service;

  @BeforeEach
  void setUp() {
    service = new CountryService(repository, new CountryMapper());
  }

  @Test
  void createRejectsDuplicateNameAndIsoCodeWithConflict() {
    CountryRequestDTO request = new CountryRequestDTO("Brasil", "br", "+55", "brl", "pt-BR");
    when(repository.existsByName("Brasil")).thenReturn(true);

    assertThatThrownBy(() -> service.create(request))
        .isInstanceOf(ApiProblemException.class)
        .satisfies(
            ex -> {
              ApiProblemException problem = (ApiProblemException) ex;
              assertThat(problem.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(problem.getCode()).isEqualTo("country.name.duplicate");
            });

    when(repository.existsByName("Brasil")).thenReturn(false);
    when(repository.existsByIsoCode("BR")).thenReturn(true);

    assertThatThrownBy(() -> service.create(request))
        .isInstanceOf(ApiProblemException.class)
        .satisfies(
            ex -> {
              ApiProblemException problem = (ApiProblemException) ex;
              assertThat(problem.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(problem.getCode()).isEqualTo("country.iso_code.duplicate");
            });
  }

  @Test
  void updateRejectsDuplicateNameAndIsoCodeAndSavesUppercasedFields() {
    CountryModel existing = country("cty-1", "Brasil", "BR");
    when(repository.findByToken("cty-1")).thenReturn(Optional.of(existing));
    when(repository.existsByName("Argentina")).thenReturn(true);

    CountryRequestDTO dupName = new CountryRequestDTO("Argentina", "br", "+55", "brl", "pt-BR");
    assertThatThrownBy(() -> service.update("cty-1", dupName))
        .isInstanceOf(ApiProblemException.class)
        .satisfies(
            ex -> {
              ApiProblemException problem = (ApiProblemException) ex;
              assertThat(problem.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(problem.getCode()).isEqualTo("country.name.duplicate");
            });

    CountryRequestDTO dupIso = new CountryRequestDTO("Brasil", "ar", "+54", "ars", "es-AR");
    when(repository.existsByIsoCode("AR")).thenReturn(true);
    assertThatThrownBy(() -> service.update("cty-1", dupIso))
        .isInstanceOf(ApiProblemException.class)
        .satisfies(
            ex -> {
              ApiProblemException problem = (ApiProblemException) ex;
              assertThat(problem.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(problem.getCode()).isEqualTo("country.iso_code.duplicate");
            });

    CountryRequestDTO valid = new CountryRequestDTO("Uruguai", "uy", "+598", "uyu", "es-UY");
    when(repository.existsByName("Uruguai")).thenReturn(false);
    when(repository.existsByIsoCode("UY")).thenReturn(false);
    when(repository.save(any(CountryModel.class))).thenAnswer(inv -> inv.getArgument(0));

    CountryResponseDTO response = service.update("cty-1", valid);
    assertThat(response.name()).isEqualTo("Uruguai");
    assertThat(response.isoCode()).isEqualTo("UY");
    assertThat(response.currency()).isEqualTo("UYU");
  }

  @Test
  void restoreHandlesDeletedAlreadyActiveAndMissingCountries() {
    CountryModel restored = country("cty-1", "Brasil", "BR");
    when(repository.existsDeletedByToken("cty-1")).thenReturn(true);
    when(repository.findByToken("cty-1")).thenReturn(Optional.of(restored));

    assertThat(service.restore("cty-1").token()).isEqualTo("cty-1");
    verify(repository).restoreByToken("cty-1");

    when(repository.existsDeletedByToken("cty-1")).thenReturn(false);
    assertThatThrownBy(() -> service.restore("cty-1"))
        .isInstanceOf(ApiProblemException.class)
        .satisfies(
            ex -> {
              ApiProblemException problem = (ApiProblemException) ex;
              assertThat(problem.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(problem.getCode()).isEqualTo("country.already_active");
            });

    when(repository.existsDeletedByToken("missing")).thenReturn(false);
    when(repository.findByToken("missing")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.restore("missing"))
        .isInstanceOf(ApiProblemException.class)
        .satisfies(
            ex -> {
              ApiProblemException problem = (ApiProblemException) ex;
              assertThat(problem.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(problem.getCode()).isEqualTo("country.not_found");
            });
  }

  @Test
  void findByTokenAndDeleteFailWithNotFoundWhenMissing() {
    when(repository.findByToken("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.findByToken("missing"))
        .isInstanceOf(ApiProblemException.class)
        .satisfies(
            ex -> assertThat(((ApiProblemException) ex).getCode()).isEqualTo("country.not_found"));

    assertThatThrownBy(() -> service.delete("missing"))
        .isInstanceOf(ApiProblemException.class)
        .satisfies(
            ex -> assertThat(((ApiProblemException) ex).getCode()).isEqualTo("country.not_found"));
  }

  private static CountryModel country(String token, String name, String isoCode) {
    CountryModel model = new CountryModel();
    model.setToken(token);
    model.setName(name);
    model.setIsoCode(isoCode);
    model.setPhoneCode("+55");
    model.setCurrency("BRL");
    model.setLocale("pt-BR");
    return model;
  }
}
