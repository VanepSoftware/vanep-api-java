package br.com.vanep.address.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.address.model.AddressModel;
import br.com.vanep.address.repository.AddressRepository;
import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AddressSeederTest {

  @Mock private AddressRepository addresses;
  @Mock private CityRepository cities;

  private AddressSeeder seeder;

  @BeforeEach
  void setUp() {
    seeder = new AddressSeeder(addresses, cities);
  }

  private CityModel cityNamed(String name) {
    CityModel city = new CityModel();
    city.setId(1L);
    city.setName(name);
    return city;
  }

  @Test
  void createsSeedAddressesWhenMissing() {
    when(cities.findFirstByNameIgnoreCase("São Paulo"))
        .thenReturn(Optional.of(cityNamed("São Paulo")));
    when(cities.findFirstByNameIgnoreCase("Campinas"))
        .thenReturn(Optional.of(cityNamed("Campinas")));
    when(addresses.existsByZipCodeAndNumber(anyString(), anyString())).thenReturn(false);

    seeder.seed();

    ArgumentCaptor<AddressModel> captor = ArgumentCaptor.forClass(AddressModel.class);
    verify(addresses, times(2)).save(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(address -> address.getStreet())
        .containsExactly("Avenida Paulista", "Rua Barão de Jaguara");
  }

  @Test
  void skipsAddressesWhenCityMissing() {
    when(cities.findFirstByNameIgnoreCase(anyString())).thenReturn(Optional.empty());

    seeder.seed();

    verify(addresses, never()).save(any(AddressModel.class));
  }

  @Test
  void skipsAddressesThatAlreadyExist() {
    when(cities.findFirstByNameIgnoreCase("São Paulo"))
        .thenReturn(Optional.of(cityNamed("São Paulo")));
    when(cities.findFirstByNameIgnoreCase("Campinas"))
        .thenReturn(Optional.of(cityNamed("Campinas")));
    when(addresses.existsByZipCodeAndNumber(anyString(), anyString())).thenReturn(true);

    seeder.seed();

    verify(addresses, never()).save(any(AddressModel.class));
  }
}
