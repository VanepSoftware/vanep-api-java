package br.com.vanep.address.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import br.com.vanep.address.model.AddressModel;
import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.district.model.DistrictModel;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AddressCatalogResolverServiceTest {
  @Mock private CityRepository cities;

  private AddressCatalogResolverService service;

  @BeforeEach
  void setUp() {
    StaticMessageSource messages = new StaticMessageSource();
    messages.setUseCodeAsDefaultMessage(true);
    service = new AddressCatalogResolverService(cities, messages);
  }

  private CityModel campinas() {
    CityModel city = new CityModel();
    city.setToken("city-campinas");
    city.setName("Campinas");
    return city;
  }

  @Test
  void appliesTheCityAndEveryPostalField() {
    CityModel city = campinas();
    AddressModel address = new AddressModel();
    when(cities.findByToken("city-campinas")).thenReturn(Optional.of(city));

    service.applyCity(
        address, "city-campinas", "Rua Barão de Jaguara", "13015904", "1481", "Apto 22", "Centro");

    assertThat(address.getCity()).isSameAs(city);
    assertThat(address.getStreet()).isEqualTo("Rua Barão de Jaguara");
    assertThat(address.getZipCode()).isEqualTo("13015904");
    assertThat(address.getNumber()).isEqualTo("1481");
    assertThat(address.getComplement()).isEqualTo("Apto 22");
    assertThat(address.getNeighborhood()).isEqualTo("Centro");
  }

  @Test
  void clearsTheColumnsThatBelongToAPlaceResolvedAddress() {
    AddressModel address = new AddressModel();
    address.setDistrict(new DistrictModel());
    address.setGooglePlaceId("place-old");
    when(cities.findByToken("city-campinas")).thenReturn(Optional.of(campinas()));

    service.applyCity(address, "city-campinas", "Rua", "13015904", null, null, null);

    assertThat(address.getDistrict()).isNull();
    assertThat(address.getGooglePlaceId()).isNull();
  }

  @Test
  void storesNullForBlankOptionalFields() {
    AddressModel address = new AddressModel();
    address.setNumber("10");
    address.setComplement("Bloco A");
    address.setNeighborhood("Centro");
    when(cities.findByToken("city-campinas")).thenReturn(Optional.of(campinas()));

    service.applyCity(address, "city-campinas", "Rua", "13015904", "  ", "", null);

    assertThat(address.getNumber()).isNull();
    assertThat(address.getComplement()).isNull();
    assertThat(address.getNeighborhood()).isNull();
  }

  @Test
  void rejectsAnUnknownCityTokenAndLeavesTheAddressUntouched() {
    AddressModel address = new AddressModel();
    address.setStreet("Rua Antiga");
    address.setNumber("10");
    when(cities.findByToken("nope")).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.applyCity(address, "nope", "Rua Nova", "13015904", "99", null, null))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            ex -> {
              assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(ex.getReason()).isEqualTo("city.not_found");
            });

    assertThat(address.getStreet()).isEqualTo("Rua Antiga");
    assertThat(address.getNumber()).isEqualTo("10");
    assertThat(address.getCity()).isNull();
  }
}
