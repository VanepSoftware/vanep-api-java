package br.com.vanep.address.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.address.dto.AddressRequestDTO;
import br.com.vanep.address.dto.AddressResponseDTO;
import br.com.vanep.address.mapper.AddressMapper;
import br.com.vanep.address.model.AddressModel;
import br.com.vanep.address.repository.AddressRepository;
import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.country.model.CountryModel;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.dependent.repository.DependentRepository;
import br.com.vanep.school.model.SchoolModel;
import br.com.vanep.school.repository.SchoolRepository;
import br.com.vanep.state.model.StateModel;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

  @Mock private AddressRepository addressRepository;
  @Mock private CityRepository cityRepository;
  @Mock private AddressMapper mapper;
  @Mock private MessageSource messages;
  @Mock private ClientRepository clients;
  @Mock private DependentRepository dependents;
  @Mock private SchoolRepository schools;

  private AddressService service;

  @BeforeEach
  void setUp() {
    service =
        new AddressService(
            addressRepository, cityRepository, mapper, messages, clients, dependents, schools);
    lenient().when(messages.getMessage(any(), any(), any())).thenAnswer(inv -> inv.getArgument(0));
    lenient().when(dependents.countByAddressId(anyLong())).thenReturn(0L);
    lenient().when(dependents.countByAddressIdAndIdNot(anyLong(), anyLong())).thenReturn(0L);
    lenient().when(schools.countByAddressId(anyLong())).thenReturn(0L);
    lenient().when(schools.countByAddressIdAndIdNot(anyLong(), anyLong())).thenReturn(0L);
  }

  private CityModel city() {
    CountryModel country = new CountryModel();
    country.setId(100L);
    country.setToken("country-br");
    country.setName("Brasil");

    StateModel state = new StateModel();
    state.setId(1L);
    state.setToken("state-sp");
    state.setUf("SP");
    state.setCountry(country);

    CityModel city = new CityModel();
    city.setId(10L);
    city.setToken("city-campinas");
    city.setName("Campinas");
    city.setState(state);
    return city;
  }

  private AddressModel addressWithToken(String token) {
    AddressModel address = new AddressModel();
    address.setToken(token);
    address.setCity(city());
    address.setZipCode("13015904");
    address.setStreet("Rua Barão de Jaguara");
    address.setNumber("1481");
    return address;
  }

  private AddressResponseDTO responseFor(String token) {
    return new AddressResponseDTO(
        token,
        "13015904",
        "Rua Barão de Jaguara",
        "1481",
        null,
        "Centro",
        "city-campinas",
        "Campinas",
        "SP",
        true,
        null);
  }

  private AddressRequestDTO requestFor(String cityToken, String street) {
    return new AddressRequestDTO(cityToken, "13015904", street, "1481", null);
  }

  @Test
  void toResponseOrNullReturnsNullWhenIdIsNull() {
    assertThat(service.toResponseOrNull(null)).isNull();
    verify(addressRepository, never()).findById(anyLong());
  }

  @Test
  void toResponseOrNullReturnsMappedAddress() {
    AddressModel address = addressWithToken("tok");
    address.setId(10L);
    AddressResponseDTO response = responseFor("tok");
    when(addressRepository.findById(10L)).thenReturn(Optional.of(address));
    when(mapper.toResponse(address)).thenReturn(response);

    assertThat(service.toResponseOrNull(10L)).isEqualTo(response);
  }

  @Test
  void toResponsesByIdsReturnsEmptyMapWithoutQueryWhenNoIds() {
    assertThat(service.toResponsesByIds(List.of())).isEmpty();
    verify(addressRepository, never()).findAllById(any());
  }

  @Test
  void toResponsesByIdsMapsPersistedRows() {
    AddressModel address = addressWithToken("tok");
    address.setId(10L);
    AddressResponseDTO response = responseFor("tok");
    when(addressRepository.findAllById(List.of(10L))).thenReturn(List.of(address));
    when(mapper.toResponse(address)).thenReturn(response);

    Map<Long, AddressResponseDTO> result = service.toResponsesByIds(List.of(10L));

    assertThat(result).containsEntry(10L, response);
  }

  @Test
  void upsertForDependentCreatesAndLinksWhenOwnerHasNone() {
    DependentModel dependent = dependentWithId(2L);
    AddressResponseDTO response = responseFor("dep-tok");
    when(dependents.findById(2L)).thenReturn(Optional.of(dependent));
    when(cityRepository.findByToken("city-campinas")).thenReturn(Optional.of(city()));
    when(addressRepository.save(any(AddressModel.class)))
        .thenAnswer(
            inv -> {
              AddressModel saved = inv.getArgument(0);
              saved.setId(20L);
              saved.setToken("dep-tok");
              return saved;
            });
    when(mapper.toResponse(any(AddressModel.class))).thenReturn(response);

    AddressResponseDTO result =
        service.upsertForDependent(2L, requestFor("city-campinas", "Rua Barão de Jaguara"));

    assertThat(result).isEqualTo(response);
    assertThat(dependent.getAddressId()).isEqualTo(20L);
    verify(dependents).save(dependent);
  }

  @Test
  void upsertForSchoolCreatesAndLinksWhenOwnerHasNone() {
    SchoolModel school = schoolWithId(3L);
    AddressResponseDTO response = responseFor("sch-tok");
    when(schools.findById(3L)).thenReturn(Optional.of(school));
    when(cityRepository.findByToken("city-campinas")).thenReturn(Optional.of(city()));
    when(addressRepository.save(any(AddressModel.class)))
        .thenAnswer(
            inv -> {
              AddressModel saved = inv.getArgument(0);
              saved.setId(30L);
              saved.setToken("sch-tok");
              return saved;
            });
    when(mapper.toResponse(any(AddressModel.class))).thenReturn(response);

    AddressResponseDTO result =
        service.upsertForSchool(3L, requestFor("city-campinas", "Rua Barão de Jaguara"));

    assertThat(result).isEqualTo(response);
    assertThat(school.getAddressId()).isEqualTo(30L);
    verify(schools).save(school);
  }

  @Test
  void upsertForDependentUpdatesSameRowOnSecondSave() {
    DependentModel dependent = dependentWithId(2L);
    dependent.setAddressId(20L);
    AddressModel existing = addressWithToken("dep-tok");
    existing.setId(20L);
    AddressResponseDTO response = responseFor("dep-tok");
    when(dependents.findById(2L)).thenReturn(Optional.of(dependent));
    when(addressRepository.findById(20L)).thenReturn(Optional.of(existing));
    when(cityRepository.findByToken("city-campinas")).thenReturn(Optional.of(city()));
    when(addressRepository.save(existing)).thenReturn(existing);
    when(mapper.toResponse(existing)).thenReturn(response);

    service.upsertForDependent(2L, requestFor("city-campinas", "Avenida Nova"));

    assertThat(existing.getStreet()).isEqualTo("Avenida Nova");
    assertThat(dependent.getAddressId()).isEqualTo(20L);
    verify(dependents, never()).save(any());
  }

  @Test
  void upsertForSchoolUpdatesSameRowOnSecondSave() {
    SchoolModel school = schoolWithId(3L);
    school.setAddressId(30L);
    AddressModel existing = addressWithToken("sch-tok");
    existing.setId(30L);
    AddressResponseDTO response = responseFor("sch-tok");
    when(schools.findById(3L)).thenReturn(Optional.of(school));
    when(addressRepository.findById(30L)).thenReturn(Optional.of(existing));
    when(cityRepository.findByToken("city-campinas")).thenReturn(Optional.of(city()));
    when(addressRepository.save(existing)).thenReturn(existing);
    when(mapper.toResponse(existing)).thenReturn(response);

    service.upsertForSchool(3L, requestFor("city-campinas", "Avenida Nova"));

    assertThat(existing.getStreet()).isEqualTo("Avenida Nova");
    assertThat(school.getAddressId()).isEqualTo(30L);
    verify(schools, never()).save(any());
  }

  @Test
  void clearForDependentDeletesAddressAndNullsPointer() {
    DependentModel dependent = dependentWithId(2L);
    dependent.setAddressId(20L);
    AddressModel existing = addressWithToken("dep-tok");
    existing.setId(20L);
    when(dependents.findById(2L)).thenReturn(Optional.of(dependent));
    when(addressRepository.findById(20L)).thenReturn(Optional.of(existing));

    service.clearForDependent(2L);

    verify(addressRepository).delete(existing);
    assertThat(dependent.getAddressId()).isNull();
    verify(dependents).save(dependent);
  }

  @Test
  void clearForSchoolDeletesAddressAndNullsPointer() {
    SchoolModel school = schoolWithId(3L);
    school.setAddressId(30L);
    AddressModel existing = addressWithToken("sch-tok");
    existing.setId(30L);
    when(schools.findById(3L)).thenReturn(Optional.of(school));
    when(addressRepository.findById(30L)).thenReturn(Optional.of(existing));

    service.clearForSchool(3L);

    verify(addressRepository).delete(existing);
    assertThat(school.getAddressId()).isNull();
    verify(schools).save(school);
  }

  private ClientModel clientWithId(Long id) {
    ClientModel client = new ClientModel();
    client.setId(id);
    return client;
  }

  private DependentModel dependentWithId(Long id) {
    DependentModel dependent = new DependentModel();
    dependent.setId(id);
    dependent.setName("Dependent");
    dependent.setClientId(1L);
    return dependent;
  }

  private SchoolModel schoolWithId(Long id) {
    SchoolModel school = new SchoolModel();
    school.setId(id);
    school.setName("School");
    return school;
  }
}
