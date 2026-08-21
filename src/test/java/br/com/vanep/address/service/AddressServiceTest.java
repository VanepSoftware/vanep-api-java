package br.com.vanep.address.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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
    lenient().when(clients.countByAddressId(anyLong())).thenReturn(0L);
    lenient().when(clients.countByAddressIdAndIdNot(anyLong(), anyLong())).thenReturn(0L);
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
    return new AddressRequestDTO(cityToken, "13015904", street, "1481", null, "Centro");
  }

  @Test
  void findAllReturnsPagedResponses() {
    AddressModel address = addressWithToken("abc");
    AddressResponseDTO response = responseFor("abc");
    when(addressRepository.findAll(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(address)));
    when(mapper.toResponse(address)).thenReturn(response);

    var result = service.findAll(Pageable.unpaged());

    assertThat(result.getContent()).containsExactly(response);
  }

  @Test
  void findByTokenReturnsResponse() {
    AddressModel address = addressWithToken("tok");
    AddressResponseDTO response = responseFor("tok");
    when(addressRepository.findByToken("tok")).thenReturn(Optional.of(address));
    when(mapper.toResponse(address)).thenReturn(response);

    assertThat(service.findByToken("tok")).isEqualTo(response);
  }

  @Test
  void findByTokenThrows404WhenNotFound() {
    when(addressRepository.findByToken("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.findByToken("missing"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }

  @Test
  void createPersistsAddress() {
    AddressModel saved = addressWithToken("tok");
    AddressResponseDTO response = responseFor("tok");
    when(cityRepository.findByToken("city-campinas")).thenReturn(Optional.of(city()));
    when(addressRepository.save(any(AddressModel.class))).thenReturn(saved);
    when(mapper.toResponse(saved)).thenReturn(response);

    AddressResponseDTO result = service.create(requestFor("city-campinas", "Rua Barão de Jaguara"));

    assertThat(result).isEqualTo(response);
    verify(addressRepository).save(any(AddressModel.class));
  }

  @Test
  void createThrows404WhenCityNotFound() {
    when(cityRepository.findByToken("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.create(requestFor("missing", "Rua Qualquer")))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
    verify(addressRepository, never()).save(any(AddressModel.class));
  }

  @Test
  void updatePersistsFields() {
    AddressModel address = addressWithToken("tok");
    AddressResponseDTO response = responseFor("tok");
    when(addressRepository.findByToken("tok")).thenReturn(Optional.of(address));
    when(cityRepository.findByToken("city-campinas")).thenReturn(Optional.of(city()));
    when(addressRepository.save(address)).thenReturn(address);
    when(mapper.toResponse(address)).thenReturn(response);

    AddressResponseDTO result = service.update("tok", requestFor("city-campinas", "Avenida Nova"));

    assertThat(result).isEqualTo(response);
    assertThat(address.getStreet()).isEqualTo("Avenida Nova");
  }

  @Test
  void updateThrows404WhenAddressNotFound() {
    when(addressRepository.findByToken("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.update("missing", requestFor("city-campinas", "Rua X")))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }

  @Test
  void updateThrows404WhenCityNotFound() {
    AddressModel address = addressWithToken("tok");
    when(addressRepository.findByToken("tok")).thenReturn(Optional.of(address));
    when(cityRepository.findByToken("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.update("tok", requestFor("missing", "Rua X")))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }

  @Test
  void deleteSoftDeletesAddress() {
    AddressModel address = addressWithToken("tok");
    when(addressRepository.findByToken("tok")).thenReturn(Optional.of(address));

    service.delete("tok");

    verify(addressRepository).delete(address);
  }

  @Test
  void deleteThrows404WhenNotFound() {
    when(addressRepository.findByToken("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.delete("missing"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }

  @Test
  void restoreRecoversDeletedAddress() {
    AddressModel address = addressWithToken("tok");
    AddressResponseDTO response = responseFor("tok");
    when(addressRepository.existsDeletedByToken("tok")).thenReturn(true);
    when(addressRepository.findByToken("tok")).thenReturn(Optional.of(address));
    when(mapper.toResponse(address)).thenReturn(response);

    AddressResponseDTO result = service.restore("tok");

    assertThat(result).isEqualTo(response);
    verify(addressRepository).restoreByToken("tok");
  }

  @Test
  void restoreThrows409WhenAlreadyActive() {
    AddressModel address = addressWithToken("tok");
    when(addressRepository.existsDeletedByToken("tok")).thenReturn(false);
    when(addressRepository.findByToken("tok")).thenReturn(Optional.of(address));

    assertThatThrownBy(() -> service.restore("tok"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("409");
    verify(addressRepository, never()).restoreByToken("tok");
  }

  @Test
  void restoreThrows404WhenNotFound() {
    when(addressRepository.existsDeletedByToken("missing")).thenReturn(false);
    when(addressRepository.findByToken("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.restore("missing"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }

  @Test
  void upsertForClientCreatesAndLinksWhenOwnerHasNone() {
    ClientModel client = clientWithId(1L);
    AddressResponseDTO response = responseFor("tok");
    when(clients.findById(1L)).thenReturn(Optional.of(client));
    when(cityRepository.findByToken("city-campinas")).thenReturn(Optional.of(city()));
    when(addressRepository.save(any(AddressModel.class)))
        .thenAnswer(
            inv -> {
              AddressModel saved = inv.getArgument(0);
              saved.setId(10L);
              saved.setToken("tok");
              return saved;
            });
    when(mapper.toResponse(any(AddressModel.class))).thenReturn(response);

    AddressResponseDTO result =
        service.upsertForClient(1L, requestFor("city-campinas", "Rua Barão de Jaguara"));

    assertThat(result).isEqualTo(response);
    assertThat(client.getAddressId()).isEqualTo(10L);
    verify(clients).save(client);
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
  void upsertForClientUpdatesSameRowOnSecondSave() {
    ClientModel client = clientWithId(1L);
    client.setAddressId(10L);
    AddressModel existing = addressWithToken("tok");
    existing.setId(10L);
    AddressResponseDTO response = responseFor("tok");
    when(clients.findById(1L)).thenReturn(Optional.of(client));
    when(addressRepository.findById(10L)).thenReturn(Optional.of(existing));
    when(cityRepository.findByToken("city-campinas")).thenReturn(Optional.of(city()));
    when(addressRepository.save(existing)).thenReturn(existing);
    when(mapper.toResponse(existing)).thenReturn(response);

    AddressResponseDTO result =
        service.upsertForClient(1L, requestFor("city-campinas", "Avenida Nova"));

    assertThat(result).isEqualTo(response);
    assertThat(existing.getStreet()).isEqualTo("Avenida Nova");
    assertThat(client.getAddressId()).isEqualTo(10L);
    verify(clients, never()).save(any());
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
  void clearForClientDeletesAddressAndNullsPointer() {
    ClientModel client = clientWithId(1L);
    client.setAddressId(10L);
    AddressModel existing = addressWithToken("tok");
    existing.setId(10L);
    when(clients.findById(1L)).thenReturn(Optional.of(client));
    when(addressRepository.findById(10L)).thenReturn(Optional.of(existing));

    service.clearForClient(1L);

    verify(addressRepository).delete(existing);
    assertThat(client.getAddressId()).isNull();
    verify(clients).save(client);
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

  @Test
  void upsertForClientThrows404WhenCityNotFound() {
    ClientModel client = clientWithId(1L);
    when(clients.findById(1L)).thenReturn(Optional.of(client));
    when(cityRepository.findByToken("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.upsertForClient(1L, requestFor("missing", "Rua Qualquer")))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            ex -> {
              ResponseStatusException status = (ResponseStatusException) ex;
              assertThat(status.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(status.getReason()).isEqualTo("city.not_found");
            });
    verify(addressRepository, never()).save(any(AddressModel.class));
  }

  @Test
  void upsertForClientThrows409WhenAddressAlreadyOwned() {
    ClientModel client = clientWithId(1L);
    client.setAddressId(10L);
    when(clients.findById(1L)).thenReturn(Optional.of(client));
    when(schools.countByAddressId(10L)).thenReturn(1L);

    assertThatThrownBy(
            () -> service.upsertForClient(1L, requestFor("city-campinas", "Rua Qualquer")))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            ex -> {
              ResponseStatusException status = (ResponseStatusException) ex;
              assertThat(status.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(status.getReason()).isEqualTo("address.already_owned");
            });
    verify(addressRepository, never()).save(any(AddressModel.class));
    verify(addressRepository, never()).findById(anyLong());
  }

  @Test
  void upsertForClientIgnoresSoftDeletedOwnersInExclusivityCount() {
    ClientModel client = clientWithId(1L);
    client.setAddressId(10L);
    AddressModel existing = addressWithToken("tok");
    existing.setId(10L);
    AddressResponseDTO response = responseFor("tok");
    when(clients.findById(1L)).thenReturn(Optional.of(client));
    when(schools.countByAddressId(10L)).thenReturn(0L);
    when(addressRepository.findById(10L)).thenReturn(Optional.of(existing));
    when(cityRepository.findByToken("city-campinas")).thenReturn(Optional.of(city()));
    when(addressRepository.save(existing)).thenReturn(existing);
    when(mapper.toResponse(existing)).thenReturn(response);

    AddressResponseDTO result =
        service.upsertForClient(1L, requestFor("city-campinas", "Avenida Nova"));

    assertThat(result).isEqualTo(response);
    verify(clients).countByAddressIdAndIdNot(10L, 1L);
    verify(dependents).countByAddressId(10L);
    verify(schools).countByAddressId(10L);
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
