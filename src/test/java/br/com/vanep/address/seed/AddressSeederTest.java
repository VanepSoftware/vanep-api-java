package br.com.vanep.address.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.address.model.AddressModel;
import br.com.vanep.address.repository.AddressRepository;
import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.dependent.repository.DependentRepository;
import br.com.vanep.school.model.SchoolModel;
import br.com.vanep.school.repository.SchoolRepository;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
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
  @Mock private ClientRepository clients;
  @Mock private DependentRepository dependents;
  @Mock private SchoolRepository schools;

  private AddressSeeder seeder;

  @BeforeEach
  void setUp() {
    seeder = new AddressSeeder(addresses, cities, clients, dependents, schools);
  }

  private CityModel campinas() {
    CityModel city = new CityModel();
    city.setId(1L);
    city.setName("Campinas");
    return city;
  }

  private ClientModel clientWithoutAddress(long id) {
    ClientModel client = new ClientModel();
    client.setId(id);
    return client;
  }

  private DependentModel dependentWithoutAddress(long id) {
    DependentModel dependent = new DependentModel();
    dependent.setId(id);
    return dependent;
  }

  private SchoolModel schoolWithoutAddress(long id) {
    SchoolModel school = new SchoolModel();
    school.setId(id);
    return school;
  }

  private void stubAddressSaveAssignsIds() {
    AtomicLong nextId = new AtomicLong(1);
    when(addresses.save(any(AddressModel.class)))
        .thenAnswer(
            invocation -> {
              AddressModel saved = invocation.getArgument(0);
              saved.setId(nextId.getAndIncrement());
              return saved;
            });
  }

  @Test
  void createsLinkedAddressForEachOwnerWithoutAddress() {
    when(cities.findFirstByNameIgnoreCase("Campinas")).thenReturn(Optional.of(campinas()));
    ClientModel client = clientWithoutAddress(10L);
    DependentModel dependent = dependentWithoutAddress(20L);
    SchoolModel school = schoolWithoutAddress(30L);
    when(clients.findAll()).thenReturn(List.of(client));
    when(dependents.findAll()).thenReturn(List.of(dependent));
    when(schools.findAll()).thenReturn(List.of(school));
    stubAddressSaveAssignsIds();

    seeder.seed();

    ArgumentCaptor<AddressModel> addressCaptor = ArgumentCaptor.forClass(AddressModel.class);
    verify(addresses, times(3)).save(addressCaptor.capture());
    assertThat(addressCaptor.getAllValues())
        .allMatch(address -> "13015904".equals(address.getZipCode()))
        .allMatch(address -> "1481".equals(address.getNumber()));
    assertThat(client.getAddressId()).isEqualTo(1L);
    assertThat(dependent.getAddressId()).isEqualTo(2L);
    assertThat(school.getAddressId()).isEqualTo(3L);
    verify(clients).save(client);
    verify(dependents).save(dependent);
    verify(schools).save(school);
  }

  @Test
  void allowsSameZipCodeAndNumberForTwoOwners() {
    when(cities.findFirstByNameIgnoreCase("Campinas")).thenReturn(Optional.of(campinas()));
    ClientModel first = clientWithoutAddress(1L);
    ClientModel second = clientWithoutAddress(2L);
    when(clients.findAll()).thenReturn(List.of(first, second));
    when(dependents.findAll()).thenReturn(List.of());
    when(schools.findAll()).thenReturn(List.of());
    stubAddressSaveAssignsIds();

    seeder.seed();

    ArgumentCaptor<AddressModel> captor = ArgumentCaptor.forClass(AddressModel.class);
    verify(addresses, times(2)).save(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(address -> address.getZipCode() + "-" + address.getNumber())
        .containsExactly("13015904-1481", "13015904-1481");
    assertThat(first.getAddressId()).isNotEqualTo(second.getAddressId());
  }

  @Test
  void skipsOwnersThatAlreadyHaveAddress() {
    when(cities.findFirstByNameIgnoreCase("Campinas")).thenReturn(Optional.of(campinas()));
    ClientModel client = clientWithoutAddress(10L);
    client.setAddressId(99L);
    when(clients.findAll()).thenReturn(List.of(client));
    when(dependents.findAll()).thenReturn(List.of());
    when(schools.findAll()).thenReturn(List.of());

    seeder.seed();

    verify(addresses, never()).save(any(AddressModel.class));
    verify(clients, never()).save(any(ClientModel.class));
  }

  @Test
  void skipsWhenCityMissing() {
    when(cities.findFirstByNameIgnoreCase("Campinas")).thenReturn(Optional.empty());

    seeder.seed();

    verify(addresses, never()).save(any(AddressModel.class));
    verify(clients, never()).findAll();
  }
}
