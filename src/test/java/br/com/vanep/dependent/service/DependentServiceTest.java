package br.com.vanep.dependent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.vanep.address.dto.AddressResponseDTO;
import br.com.vanep.address.dto.DependentAddressRequestDTO;
import br.com.vanep.address.service.AddressService;
import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.dependent.dto.DependentCreateDTO;
import br.com.vanep.dependent.dto.DependentResponseDTO;
import br.com.vanep.dependent.dto.DependentSchoolDTO;
import br.com.vanep.dependent.dto.DependentSchoolDayRequestDTO;
import br.com.vanep.dependent.dto.DependentSchoolDayResponseDTO;
import br.com.vanep.dependent.dto.DependentSchoolDaysRequestDTO;
import br.com.vanep.dependent.dto.DependentUpdateDTO;
import br.com.vanep.dependent.mapper.DependentMapper;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.dependent.model.DependentSchoolDayModel;
import br.com.vanep.dependent.repository.DependentRepository;
import br.com.vanep.dependent.repository.DependentSchoolDayRepository;
import br.com.vanep.school.model.SchoolModel;
import br.com.vanep.school.repository.SchoolRepository;
import br.com.vanep.shared.enums.SchoolShift;
import br.com.vanep.user.enums.Gender;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class DependentServiceTest {
  private static final String CLIENT_EMAIL = "ana.souza@vanep.com";
  private static final Long CLIENT_ID = 100L;
  private static final Long DEPENDENT_ID = 55L;
  private static final Long SCHOOL_ID = 7L;
  private static final String TOKEN = "tok";
  private static final String SCHOOL_TOKEN = "school-tok";
  private static final String SCHOOL_NAME = "Escola Teste";

  @Mock private DependentRepository dependents;
  @Mock private DependentSchoolDayRepository schoolDays;
  @Mock private ClientRepository clients;
  @Mock private SchoolRepository schools;
  @Mock private UserRepository users;
  @Mock private DependentMapper mapper;
  @Mock private AddressService addressService;
  @Mock private MessageSource messages;

  @Captor private ArgumentCaptor<List<DependentSchoolDayModel>> savedDays;

  private DependentService service;

  @BeforeEach
  void setUp() {
    service =
        new DependentService(
            dependents, schoolDays, clients, schools, users, mapper, addressService, messages);
    lenient().when(messages.getMessage(any(), any(), any())).thenAnswer(inv -> inv.getArgument(0));
  }

  private Jwt clientJwt() {
    Jwt jwt = mock(Jwt.class);
    lenient().when(jwt.getSubject()).thenReturn(CLIENT_EMAIL);
    lenient().when(jwt.getClaim("roles")).thenReturn(List.of("ROLE_CLIENT"));
    return jwt;
  }

  private void stubOwnershipResolution() {
    UserModel user = new UserModel();
    user.setId(10L);
    user.setEmail(CLIENT_EMAIL);
    ClientModel client = new ClientModel();
    client.setId(CLIENT_ID);
    lenient().when(users.findByEmail(CLIENT_EMAIL)).thenReturn(Optional.of(user));
    lenient().when(clients.findByUserId(10L)).thenReturn(Optional.of(client));
  }

  private void stubResponseMapping() {
    ClientModel client = new ClientModel();
    client.setId(CLIENT_ID);
    client.setToken("ctok");
    lenient().when(clients.findById(CLIENT_ID)).thenReturn(Optional.of(client));
    lenient()
        .when(
            mapper.toResponse(
                any(DependentModel.class),
                eq("ctok"),
                nullable(DependentSchoolDTO.class),
                nullable(AddressResponseDTO.class)))
        .thenReturn(response(null));
  }

  private SchoolModel school() {
    return school(SCHOOL_ID, SCHOOL_TOKEN, SCHOOL_NAME);
  }

  private SchoolModel school(Long id, String token, String name) {
    SchoolModel model = new SchoolModel();
    model.setId(id);
    model.setToken(token);
    model.setName(name);
    return model;
  }

  private DependentUpdateDTO schoolOnly(JsonNullable<String> schoolToken) {
    return patch(
        JsonNullable.undefined(),
        JsonNullable.undefined(),
        JsonNullable.undefined(),
        JsonNullable.undefined(),
        JsonNullable.undefined(),
        JsonNullable.undefined(),
        JsonNullable.undefined(),
        JsonNullable.undefined(),
        JsonNullable.undefined(),
        schoolToken);
  }

  private DependentResponseDTO response(AddressResponseDTO address) {
    return new DependentResponseDTO(
        TOKEN,
        null,
        "Kid",
        LocalDate.of(2015, 3, 20),
        Gender.MALE,
        "11111111111",
        "11988887777",
        "kid@vanep.com",
        false,
        false,
        null,
        address,
        null,
        null);
  }

  private AddressResponseDTO addressResponse() {
    return new AddressResponseDTO(
        "addr-tok",
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

  private DependentAddressRequestDTO addressRequest() {
    return new DependentAddressRequestDTO(
        "city-campinas", "Rua Barão de Jaguara", "13015904", "1481", null, "Centro");
  }

  private DependentModel dependent(boolean isDefault) {
    DependentModel model = new DependentModel();
    model.setId(DEPENDENT_ID);
    model.setToken(TOKEN);
    model.setClientId(CLIENT_ID);
    model.setName("Kid");
    model.setPhone("11988887777");
    model.setEmail("kid@vanep.com");
    model.setBirthDate(LocalDate.of(2015, 3, 20));
    model.setDocument("11111111111");
    model.setDefaultDependent(isDefault);
    return model;
  }

  private DependentUpdateDTO patch(
      JsonNullable<String> name,
      JsonNullable<LocalDate> birthDate,
      JsonNullable<Gender> gender,
      JsonNullable<String> document,
      JsonNullable<String> phone,
      JsonNullable<String> email,
      JsonNullable<Boolean> isSelf,
      JsonNullable<Boolean> isDefault,
      JsonNullable<DependentAddressRequestDTO> address,
      JsonNullable<String> schoolToken) {
    return new DependentUpdateDTO(
        name, birthDate, gender, document, phone, email, isSelf, isDefault, address, schoolToken);
  }

  private DependentUpdateDTO nameOnly(String name) {
    return patch(
        JsonNullable.of(name),
        JsonNullable.undefined(),
        JsonNullable.undefined(),
        JsonNullable.undefined(),
        JsonNullable.undefined(),
        JsonNullable.undefined(),
        JsonNullable.undefined(),
        JsonNullable.undefined(),
        JsonNullable.undefined(),
        JsonNullable.undefined());
  }

  @Test
  void firstDependentBecomesDefault() {
    DependentCreateDTO dto = new DependentCreateDTO();
    dto.setName("Kid");
    DependentModel model = dependent(false);

    stubOwnershipResolution();
    stubResponseMapping();
    when(mapper.toModel(dto, CLIENT_ID)).thenReturn(model);
    when(dependents.countByClientId(CLIENT_ID)).thenReturn(0L);
    when(dependents.save(model)).thenReturn(model);

    service.create(clientJwt(), dto);

    assertThat(model.isDefaultDependent()).isTrue();
    verify(addressService, never()).upsertForDependent(any(), any());
  }

  @Test
  void additionalDependentIsNotDefaultByDefault() {
    DependentCreateDTO dto = new DependentCreateDTO();
    dto.setName("Kid");
    DependentModel model = dependent(false);

    stubOwnershipResolution();
    stubResponseMapping();
    when(mapper.toModel(dto, CLIENT_ID)).thenReturn(model);
    when(dependents.countByClientId(CLIENT_ID)).thenReturn(2L);
    when(dependents.save(model)).thenReturn(model);

    service.create(clientJwt(), dto);

    assertThat(model.isDefaultDependent()).isFalse();
  }

  @Test
  void settingDefaultOnCreateUnsetsOthers() {
    DependentCreateDTO dto = new DependentCreateDTO();
    dto.setName("Kid");
    dto.setIsDefault(true);
    DependentModel model = dependent(false);
    DependentModel previousDefault = dependent(true);
    previousDefault.setToken("old");

    stubOwnershipResolution();
    stubResponseMapping();
    when(mapper.toModel(dto, CLIENT_ID)).thenReturn(model);
    when(dependents.countByClientId(CLIENT_ID)).thenReturn(1L);
    when(dependents.findByClientId(CLIENT_ID)).thenReturn(List.of(previousDefault));
    when(dependents.save(any(DependentModel.class))).thenReturn(model);

    service.create(clientJwt(), dto);

    assertThat(model.isDefaultDependent()).isTrue();
    assertThat(previousDefault.isDefaultDependent()).isFalse();
  }

  @Test
  void createWithNestedAddressUpsertsAfterSave() {
    DependentCreateDTO dto = new DependentCreateDTO();
    dto.setName("Kid");
    dto.setAddress(addressRequest());
    DependentModel model = dependent(false);
    AddressResponseDTO address = addressResponse();

    stubOwnershipResolution();
    stubResponseMapping();
    when(mapper.toModel(dto, CLIENT_ID)).thenReturn(model);
    when(dependents.countByClientId(CLIENT_ID)).thenReturn(0L);
    when(dependents.save(model)).thenReturn(model);
    when(addressService.upsertForDependent(DEPENDENT_ID, dto.getAddress())).thenReturn(address);
    when(dependents.findById(DEPENDENT_ID)).thenReturn(Optional.of(model));

    service.create(clientJwt(), dto);

    InOrder order = inOrder(dependents, addressService);
    order.verify(dependents).save(model);
    order.verify(addressService).upsertForDependent(DEPENDENT_ID, dto.getAddress());
  }

  @Test
  void createWithSchoolTokenSetsSchoolId() {
    DependentCreateDTO dto = new DependentCreateDTO();
    dto.setName("Kid");
    dto.setSchoolToken(SCHOOL_TOKEN);
    DependentModel model = dependent(false);
    SchoolModel linked = school();

    stubOwnershipResolution();
    stubResponseMapping();
    when(mapper.toModel(dto, CLIENT_ID)).thenReturn(model);
    when(dependents.countByClientId(CLIENT_ID)).thenReturn(0L);
    when(schools.findByToken(SCHOOL_TOKEN)).thenReturn(Optional.of(linked));
    when(schools.findById(SCHOOL_ID)).thenReturn(Optional.of(linked));
    when(dependents.save(model)).thenReturn(model);

    service.create(clientJwt(), dto);

    assertThat(model.getSchoolId()).isEqualTo(SCHOOL_ID);
    verify(mapper)
        .toResponse(model, "ctok", new DependentSchoolDTO(SCHOOL_TOKEN, SCHOOL_NAME, null), null);
  }

  @Test
  void createWithUnknownSchoolTokenThrows404() {
    DependentCreateDTO dto = new DependentCreateDTO();
    dto.setName("Kid");
    dto.setSchoolToken("missing-tok");
    DependentModel model = dependent(false);

    stubOwnershipResolution();
    when(mapper.toModel(dto, CLIENT_ID)).thenReturn(model);
    when(schools.findByToken("missing-tok")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.create(clientJwt(), dto))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    verify(dependents, never()).save(any());
  }

  @Test
  void createWithBlankSchoolTokenThrows400() {
    DependentCreateDTO dto = new DependentCreateDTO();
    dto.setName("Kid");
    dto.setSchoolToken("  ");
    DependentModel model = dependent(false);

    stubOwnershipResolution();
    when(mapper.toModel(dto, CLIENT_ID)).thenReturn(model);

    assertThatThrownBy(() -> service.create(clientJwt(), dto))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    verify(schools, never()).findByToken(any());
    verify(dependents, never()).save(any());
  }

  @Test
  void deletingDefaultPromotesRemainingWhenExactlyOneLeft() {
    DependentModel toDelete = dependent(true);
    DependentModel remaining = dependent(false);
    remaining.setToken("other");

    stubOwnershipResolution();
    when(dependents.lockByToken(TOKEN)).thenReturn(Optional.of(toDelete));
    when(dependents.findByClientId(CLIENT_ID)).thenReturn(List.of(remaining));

    service.delete(clientJwt(), TOKEN);

    InOrder order = inOrder(addressService, dependents);
    order.verify(addressService).clearForDependent(DEPENDENT_ID);
    order.verify(dependents).delete(toDelete);
    assertThat(remaining.isDefaultDependent()).isTrue();
  }

  @Test
  void deletingDefaultDoesNotPromoteWhenMultipleRemain() {
    DependentModel toDelete = dependent(true);
    DependentModel first = dependent(false);
    DependentModel second = dependent(false);

    stubOwnershipResolution();
    when(dependents.lockByToken(TOKEN)).thenReturn(Optional.of(toDelete));
    when(dependents.findByClientId(CLIENT_ID)).thenReturn(List.of(first, second));

    service.delete(clientJwt(), TOKEN);

    verify(addressService).clearForDependent(DEPENDENT_ID);
    verify(dependents).delete(toDelete);
    verify(dependents, never()).save(any());
    assertThat(first.isDefaultDependent()).isFalse();
    assertThat(second.isDefaultDependent()).isFalse();
  }

  @Test
  void restoreDoesNotResurrectAddress() {
    DependentModel restored = dependent(false);
    restored.setAddressId(null);
    DependentResponseDTO mapped = response(null);

    stubOwnershipResolution();
    stubResponseMapping();
    when(dependents.findByToken(TOKEN))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(restored));
    when(dependents.findClientIdOfDeletedByToken(TOKEN)).thenReturn(Optional.of(CLIENT_ID));
    when(addressService.toResponseOrNull(null)).thenReturn(null);
    when(mapper.toResponse(restored, "ctok", null, null)).thenReturn(mapped);

    DependentResponseDTO result = service.restore(clientJwt(), TOKEN);
    verify(dependents).clearActiveDefaultsIfDeletedIsDefault(CLIENT_ID, TOKEN);

    assertThat(result.address()).isNull();
    verify(addressService, never()).upsertForDependent(any(), any());
  }

  @Test
  void updateNameOnlyDoesNotTouchAddressOrNullableScalars() {
    DependentModel model = dependent(true);
    model.setAddressId(10L);

    stubOwnershipResolution();
    stubResponseMapping();
    when(dependents.lockByToken(TOKEN)).thenReturn(Optional.of(model));
    when(dependents.save(model)).thenReturn(model);
    when(addressService.toResponseOrNull(10L)).thenReturn(addressResponse());

    service.update(clientJwt(), TOKEN, nameOnly("Novo"));

    assertThat(model.getName()).isEqualTo("Novo");
    assertThat(model.getPhone()).isEqualTo("11988887777");
    assertThat(model.getEmail()).isEqualTo("kid@vanep.com");
    assertThat(model.getBirthDate()).isEqualTo(LocalDate.of(2015, 3, 20));
    assertThat(model.getAddressId()).isEqualTo(10L);
    verify(addressService, never()).upsertForDependent(any(), any());
    verify(addressService, never()).clearForDependent(any());
  }

  @Test
  void updatePresentNullPhoneClearsPhone() {
    DependentModel model = dependent(true);

    stubOwnershipResolution();
    stubResponseMapping();
    when(dependents.lockByToken(TOKEN)).thenReturn(Optional.of(model));
    when(dependents.save(model)).thenReturn(model);

    service.update(
        clientJwt(),
        TOKEN,
        patch(
            JsonNullable.undefined(),
            JsonNullable.undefined(),
            JsonNullable.undefined(),
            JsonNullable.undefined(),
            JsonNullable.of(null),
            JsonNullable.undefined(),
            JsonNullable.undefined(),
            JsonNullable.undefined(),
            JsonNullable.undefined(),
            JsonNullable.undefined()));

    assertThat(model.getPhone()).isNull();
    assertThat(model.getEmail()).isEqualTo("kid@vanep.com");
  }

  @Test
  void updatePresentNullAddressClearsAddress() {
    DependentModel model = dependent(true);
    model.setAddressId(10L);

    stubOwnershipResolution();
    stubResponseMapping();
    when(dependents.lockByToken(TOKEN)).thenReturn(Optional.of(model));
    when(dependents.save(model)).thenReturn(model);
    when(dependents.findById(DEPENDENT_ID)).thenReturn(Optional.of(model));

    service.update(
        clientJwt(),
        TOKEN,
        patch(
            JsonNullable.undefined(),
            JsonNullable.undefined(),
            JsonNullable.undefined(),
            JsonNullable.undefined(),
            JsonNullable.undefined(),
            JsonNullable.undefined(),
            JsonNullable.undefined(),
            JsonNullable.undefined(),
            JsonNullable.of(null),
            JsonNullable.undefined()));

    verify(addressService).clearForDependent(DEPENDENT_ID);
    verify(addressService, never()).upsertForDependent(any(), any());
  }

  @Test
  void updateNestedAddressUpsertsOwnedRow() {
    DependentModel model = dependent(true);
    DependentAddressRequestDTO request = addressRequest();

    stubOwnershipResolution();
    stubResponseMapping();
    when(dependents.lockByToken(TOKEN)).thenReturn(Optional.of(model));
    when(dependents.save(model)).thenReturn(model);
    when(addressService.upsertForDependent(DEPENDENT_ID, request)).thenReturn(addressResponse());
    when(dependents.findById(DEPENDENT_ID)).thenReturn(Optional.of(model));

    service.update(
        clientJwt(),
        TOKEN,
        patch(
            JsonNullable.undefined(),
            JsonNullable.undefined(),
            JsonNullable.undefined(),
            JsonNullable.undefined(),
            JsonNullable.undefined(),
            JsonNullable.undefined(),
            JsonNullable.undefined(),
            JsonNullable.undefined(),
            JsonNullable.of(request),
            JsonNullable.undefined()));

    verify(addressService).upsertForDependent(DEPENDENT_ID, request);
  }

  @Test
  void updatePresentSchoolTokenSetsSchoolId() {
    DependentModel model = dependent(true);
    SchoolModel linked = school();

    stubOwnershipResolution();
    stubResponseMapping();
    when(dependents.lockByToken(TOKEN)).thenReturn(Optional.of(model));
    when(schools.findByToken(SCHOOL_TOKEN)).thenReturn(Optional.of(linked));
    when(schools.findById(SCHOOL_ID)).thenReturn(Optional.of(linked));
    when(dependents.save(model)).thenReturn(model);

    service.update(clientJwt(), TOKEN, schoolOnly(JsonNullable.of(SCHOOL_TOKEN)));

    assertThat(model.getSchoolId()).isEqualTo(SCHOOL_ID);
  }

  @Test
  void updatePresentNullSchoolTokenClearsSchoolId() {
    DependentModel model = dependent(true);
    model.setSchoolId(SCHOOL_ID);

    stubOwnershipResolution();
    stubResponseMapping();
    when(dependents.lockByToken(TOKEN)).thenReturn(Optional.of(model));
    when(dependents.save(model)).thenReturn(model);

    service.update(clientJwt(), TOKEN, schoolOnly(JsonNullable.of(null)));

    assertThat(model.getSchoolId()).isNull();
    verify(schools, never()).findByToken(any());
  }

  @Test
  void updateOmitSchoolTokenLeavesSchoolId() {
    DependentModel model = dependent(true);
    model.setSchoolId(SCHOOL_ID);
    SchoolModel linked = school();

    stubOwnershipResolution();
    stubResponseMapping();
    when(dependents.lockByToken(TOKEN)).thenReturn(Optional.of(model));
    when(schools.findById(SCHOOL_ID)).thenReturn(Optional.of(linked));
    when(dependents.save(model)).thenReturn(model);

    service.update(clientJwt(), TOKEN, nameOnly("Novo"));

    assertThat(model.getSchoolId()).isEqualTo(SCHOOL_ID);
    verify(schools, never()).findByToken(any());
  }

  @Test
  void updateUnknownSchoolTokenThrows404() {
    DependentModel model = dependent(true);

    stubOwnershipResolution();
    when(dependents.lockByToken(TOKEN)).thenReturn(Optional.of(model));
    when(schools.findByToken("missing-tok")).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.update(clientJwt(), TOKEN, schoolOnly(JsonNullable.of("missing-tok"))))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    verify(dependents, never()).save(any());
  }

  @Test
  void updateBlankSchoolTokenThrows400() {
    DependentModel model = dependent(true);
    model.setSchoolId(SCHOOL_ID);

    stubOwnershipResolution();
    when(dependents.lockByToken(TOKEN)).thenReturn(Optional.of(model));

    assertThatThrownBy(() -> service.update(clientJwt(), TOKEN, schoolOnly(JsonNullable.of(""))))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(model.getSchoolId()).isEqualTo(SCHOOL_ID);
    verify(dependents, never()).save(any());
  }

  @Test
  void listBatchesSchoolLookup() {
    DependentModel first = dependent(true);
    first.setSchoolId(SCHOOL_ID);
    DependentModel second = dependent(false);
    second.setId(56L);
    second.setToken("other");
    second.setSchoolId(8L);
    SchoolModel firstSchool = school();
    SchoolModel secondSchool = school(8L, "school-b", "Escola B");
    ClientModel client = new ClientModel();
    client.setId(CLIENT_ID);
    client.setToken("ctok");

    stubOwnershipResolution();
    when(dependents.findByClientId(CLIENT_ID)).thenReturn(List.of(first, second));
    when(clients.findAllById(List.of(CLIENT_ID))).thenReturn(List.of(client));
    when(schools.findAllById(List.of(SCHOOL_ID, 8L)))
        .thenReturn(List.of(firstSchool, secondSchool));
    when(addressService.toResponsesByIds(List.of())).thenReturn(Map.of());
    when(mapper.toResponse(any(), eq("ctok"), any(), nullable(AddressResponseDTO.class)))
        .thenReturn(response(null));

    service.list(clientJwt());

    verify(schools).findAllById(List.of(SCHOOL_ID, 8L));
    verify(schools, never()).findById(any());
    verify(mapper)
        .toResponse(first, "ctok", new DependentSchoolDTO(SCHOOL_TOKEN, SCHOOL_NAME, null), null);
    verify(mapper)
        .toResponse(second, "ctok", new DependentSchoolDTO("school-b", "Escola B", null), null);
  }

  @Test
  void listReadsTheDependentAndSchoolAddressesInOneQuery() {
    DependentModel first = dependent(true);
    first.setSchoolId(SCHOOL_ID);
    first.setAddressId(10L);
    SchoolModel firstSchool = school();
    firstSchool.setAddressId(20L);
    ClientModel client = new ClientModel();
    client.setId(CLIENT_ID);
    client.setToken("ctok");
    AddressResponseDTO home = addressResponse();
    AddressResponseDTO schoolAddress = schoolAddressResponse();

    stubOwnershipResolution();
    when(dependents.findByClientId(CLIENT_ID)).thenReturn(List.of(first));
    when(clients.findAllById(List.of(CLIENT_ID))).thenReturn(List.of(client));
    when(schools.findAllById(List.of(SCHOOL_ID))).thenReturn(List.of(firstSchool));
    when(addressService.toResponsesByIds(List.of(10L, 20L)))
        .thenReturn(Map.of(10L, home, 20L, schoolAddress));
    when(mapper.toResponse(any(), eq("ctok"), any(), nullable(AddressResponseDTO.class)))
        .thenReturn(response(home));

    service.list(clientJwt());

    verify(addressService).toResponsesByIds(List.of(10L, 20L));
    verify(addressService, never()).toResponseOrNull(any());
    verify(mapper)
        .toResponse(
            first, "ctok", new DependentSchoolDTO(SCHOOL_TOKEN, SCHOOL_NAME, schoolAddress), home);
  }

  @Test
  void readsTheSchoolAddressOfOneDependent() {
    DependentModel model = dependent(true);
    model.setSchoolId(SCHOOL_ID);
    SchoolModel linked = school();
    linked.setAddressId(20L);

    stubOwnershipResolution();
    stubResponseMapping();
    when(dependents.findByToken(TOKEN)).thenReturn(Optional.of(model));
    when(schools.findById(SCHOOL_ID)).thenReturn(Optional.of(linked));
    when(addressService.toResponseOrNull(20L)).thenReturn(schoolAddressResponse());

    service.getByToken(clientJwt(), TOKEN);

    verify(mapper)
        .toResponse(
            model,
            "ctok",
            new DependentSchoolDTO(SCHOOL_TOKEN, SCHOOL_NAME, schoolAddressResponse()),
            null);
  }

  private AddressResponseDTO schoolAddressResponse() {
    return new AddressResponseDTO(
        "school-addr",
        "72145450",
        "QI 21",
        null,
        null,
        "Taguatinga Norte",
        "city-brasilia",
        "Brasília",
        "DF",
        true,
        null);
  }

  @Test
  void updatePresentBlankNameThrows400AndLeavesStoredName() {
    DependentModel model = dependent(true);

    stubOwnershipResolution();
    when(dependents.lockByToken(TOKEN)).thenReturn(Optional.of(model));

    assertThatThrownBy(() -> service.update(clientJwt(), TOKEN, nameOnly("")))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(model.getName()).isEqualTo("Kid");
    verify(dependents, never()).save(any());
  }

  @Test
  void updatePresentNullNameThrows400() {
    DependentModel model = dependent(true);

    stubOwnershipResolution();
    when(dependents.lockByToken(TOKEN)).thenReturn(Optional.of(model));

    assertThatThrownBy(() -> service.update(clientJwt(), TOKEN, nameOnly(null)))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void updatePresentNullIsDefaultThrows400() {
    DependentModel model = dependent(true);

    stubOwnershipResolution();
    when(dependents.lockByToken(TOKEN)).thenReturn(Optional.of(model));

    assertThatThrownBy(
            () ->
                service.update(
                    clientJwt(),
                    TOKEN,
                    patch(
                        JsonNullable.undefined(),
                        JsonNullable.undefined(),
                        JsonNullable.undefined(),
                        JsonNullable.undefined(),
                        JsonNullable.undefined(),
                        JsonNullable.undefined(),
                        JsonNullable.undefined(),
                        JsonNullable.of(null),
                        JsonNullable.undefined(),
                        JsonNullable.undefined())))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(model.isDefaultDependent()).isTrue();
  }

  @Test
  void updateIsDefaultTrueUnsetsOtherDefaults() {
    DependentModel model = dependent(false);
    DependentModel previousDefault = dependent(true);
    previousDefault.setToken("old");

    stubOwnershipResolution();
    stubResponseMapping();
    when(dependents.lockByToken(TOKEN)).thenReturn(Optional.of(model));
    when(dependents.findByClientId(CLIENT_ID)).thenReturn(List.of(previousDefault, model));
    when(dependents.save(any(DependentModel.class))).thenReturn(model);

    service.update(
        clientJwt(),
        TOKEN,
        patch(
            JsonNullable.undefined(),
            JsonNullable.undefined(),
            JsonNullable.undefined(),
            JsonNullable.undefined(),
            JsonNullable.undefined(),
            JsonNullable.undefined(),
            JsonNullable.undefined(),
            JsonNullable.of(true),
            JsonNullable.undefined(),
            JsonNullable.undefined()));

    assertThat(model.isDefaultDependent()).isTrue();
    assertThat(previousDefault.isDefaultDependent()).isFalse();
  }

  @Test
  void updateDuplicateDocumentOnAnotherDependentThrows409() {
    DependentModel model = dependent(true);
    model.setDocument("22222222222");

    stubOwnershipResolution();
    when(dependents.lockByToken(TOKEN)).thenReturn(Optional.of(model));
    when(dependents.existsByDocumentAndTokenNot("11111111111", TOKEN)).thenReturn(true);

    assertThatThrownBy(
            () ->
                service.update(
                    clientJwt(),
                    TOKEN,
                    patch(
                        JsonNullable.undefined(),
                        JsonNullable.undefined(),
                        JsonNullable.undefined(),
                        JsonNullable.of("11111111111"),
                        JsonNullable.undefined(),
                        JsonNullable.undefined(),
                        JsonNullable.undefined(),
                        JsonNullable.undefined(),
                        JsonNullable.undefined(),
                        JsonNullable.undefined())))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(model.getDocument()).isEqualTo("22222222222");
    verify(dependents, never()).save(any());
  }

  @Test
  void updateSameDocumentResentIsNoOpWithoutDuplicateCheck() {
    DependentModel model = dependent(true);

    stubOwnershipResolution();
    stubResponseMapping();
    when(dependents.lockByToken(TOKEN)).thenReturn(Optional.of(model));
    when(dependents.save(model)).thenReturn(model);

    service.update(
        clientJwt(),
        TOKEN,
        patch(
            JsonNullable.undefined(),
            JsonNullable.undefined(),
            JsonNullable.undefined(),
            JsonNullable.of("11111111111"),
            JsonNullable.undefined(),
            JsonNullable.undefined(),
            JsonNullable.undefined(),
            JsonNullable.undefined(),
            JsonNullable.undefined(),
            JsonNullable.undefined()));

    assertThat(model.getDocument()).isEqualTo("11111111111");
    verify(dependents, never()).existsByDocumentAndTokenNot(any(), any());
  }

  @Test
  void readsTheSchoolDaysOfAnOwnDependent() {
    stubOwnershipResolution();
    List<DependentSchoolDayModel> stored =
        List.of(schoolDay(DayOfWeek.MONDAY, SchoolShift.MORNING));
    List<DependentSchoolDayResponseDTO> days =
        List.of(new DependentSchoolDayResponseDTO(DayOfWeek.MONDAY, SchoolShift.MORNING));
    when(dependents.findByToken(TOKEN)).thenReturn(Optional.of(dependent(true)));
    when(schoolDays.findByDependentId(DEPENDENT_ID)).thenReturn(stored);
    when(mapper.toSchoolDayResponses(stored)).thenReturn(days);

    assertThat(service.findSchoolDays(clientJwt(), TOKEN)).isEqualTo(days);
  }

  @Test
  void anotherClientCannotReadTheSchoolDays() {
    stubOwnershipResolution();
    DependentModel othersDependent = dependent(true);
    othersDependent.setClientId(999L);
    when(dependents.findByToken(TOKEN)).thenReturn(Optional.of(othersDependent));

    assertThatThrownBy(() -> service.findSchoolDays(clientJwt(), TOKEN))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    verifyNoInteractions(schoolDays);
  }

  @Test
  void replacingTheSchoolDaysRemovesTheOldOnesBeforeSavingTheNewOnes() {
    stubOwnershipResolution();
    stubSchoolDayMapping();
    List<DependentSchoolDayModel> old = List.of(schoolDay(DayOfWeek.MONDAY, SchoolShift.MORNING));
    when(dependents.lockByToken(TOKEN)).thenReturn(Optional.of(dependent(true)));
    when(schoolDays.findByDependentId(DEPENDENT_ID)).thenReturn(old);

    service.replaceSchoolDays(
        clientJwt(),
        TOKEN,
        schoolDaysRequest(
            new DependentSchoolDayRequestDTO(DayOfWeek.TUESDAY, SchoolShift.FULLTIME),
            new DependentSchoolDayRequestDTO(DayOfWeek.MONDAY, SchoolShift.AFTERNOON)));

    InOrder order = inOrder(schoolDays);
    order.verify(schoolDays).deleteAll(old);
    order.verify(schoolDays).flush();
    order.verify(schoolDays).saveAll(savedDays.capture());
    assertThat(savedDays.getValue())
        .extracting(
            day -> day.getDependentId(), day -> day.getWeekday(), day -> day.getSchoolShift())
        .containsExactly(
            tuple(DEPENDENT_ID, DayOfWeek.TUESDAY, SchoolShift.FULLTIME),
            tuple(DEPENDENT_ID, DayOfWeek.MONDAY, SchoolShift.AFTERNOON));
  }

  @Test
  void anEmptyListClearsTheSchoolDays() {
    stubOwnershipResolution();
    List<DependentSchoolDayModel> old = List.of(schoolDay(DayOfWeek.MONDAY, SchoolShift.MORNING));
    when(dependents.lockByToken(TOKEN)).thenReturn(Optional.of(dependent(true)));
    when(schoolDays.findByDependentId(DEPENDENT_ID)).thenReturn(old);

    service.replaceSchoolDays(clientJwt(), TOKEN, schoolDaysRequest());

    verify(schoolDays).deleteAll(old);
    verify(schoolDays).saveAll(savedDays.capture());
    assertThat(savedDays.getValue()).isEmpty();
  }

  @Test
  void theSameWeekdayTwiceIsRefusedAndTheGridIsUnchanged() {
    stubOwnershipResolution();
    when(dependents.lockByToken(TOKEN)).thenReturn(Optional.of(dependent(true)));

    assertThatThrownBy(
            () ->
                service.replaceSchoolDays(
                    clientJwt(),
                    TOKEN,
                    schoolDaysRequest(
                        new DependentSchoolDayRequestDTO(DayOfWeek.MONDAY, SchoolShift.MORNING),
                        new DependentSchoolDayRequestDTO(DayOfWeek.MONDAY, SchoolShift.FULLTIME))))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> {
              assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
              assertThat(e.getReason()).isEqualTo("dependent.school_days.weekday_duplicate");
            });
    verifyNoInteractions(schoolDays);
  }

  @Test
  void anotherClientCannotReplaceTheSchoolDays() {
    stubOwnershipResolution();
    DependentModel othersDependent = dependent(true);
    othersDependent.setClientId(999L);
    when(dependents.lockByToken(TOKEN)).thenReturn(Optional.of(othersDependent));

    assertThatThrownBy(
            () ->
                service.replaceSchoolDays(
                    clientJwt(),
                    TOKEN,
                    schoolDaysRequest(
                        new DependentSchoolDayRequestDTO(DayOfWeek.MONDAY, SchoolShift.MORNING))))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    verifyNoInteractions(schoolDays);
  }

  @Test
  void anUnknownDependentHasNoSchoolDaysToReplace() {
    when(dependents.lockByToken("ghost")).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.replaceSchoolDays(
                    clientJwt(),
                    "ghost",
                    schoolDaysRequest(
                        new DependentSchoolDayRequestDTO(DayOfWeek.MONDAY, SchoolShift.MORNING))))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    verifyNoInteractions(schoolDays);
  }

  @Test
  void removalLocksTheDependentBeforeCheckingItsContractsAndNegotiations() {
    DependentModel model = dependent(false);
    stubOwnershipResolution();
    when(dependents.lockByToken(TOKEN)).thenReturn(Optional.of(model));

    service.delete(clientJwt(), TOKEN);

    InOrder order = inOrder(dependents);
    order.verify(dependents).lockByToken(TOKEN);
    order.verify(dependents).existsInOpenContract(DEPENDENT_ID);
    order.verify(dependents).existsInOpenServiceRequest(DEPENDENT_ID);
    order.verify(dependents).delete(model);
  }

  @Test
  void aDependentInAnOpenServiceRequestIsNotRemoved() {
    stubOwnershipResolution();
    when(dependents.lockByToken(TOKEN)).thenReturn(Optional.of(dependent(false)));
    when(dependents.existsInOpenServiceRequest(DEPENDENT_ID)).thenReturn(true);

    assertLocked(() -> service.delete(clientJwt(), TOKEN), "dependent.open_negotiation");
    verify(dependents, never()).delete(any());
    verifyNoInteractions(addressService);
  }

  @Test
  void aDependentWithAContractInProgressIsNotRemoved() {
    stubOwnershipResolution();
    when(dependents.lockByToken(TOKEN)).thenReturn(Optional.of(dependent(false)));
    when(dependents.existsInOpenContract(DEPENDENT_ID)).thenReturn(true);

    assertLocked(() -> service.delete(clientJwt(), TOKEN), "dependent.open_contract");
    verify(dependents, never()).delete(any());
    verifyNoInteractions(addressService);
  }

  @Test
  void theAdminIsLockedOutByTheContractToo() {
    when(dependents.lockByToken(TOKEN)).thenReturn(Optional.of(dependent(false)));
    when(dependents.existsInOpenContract(DEPENDENT_ID)).thenReturn(true);

    assertLocked(() -> service.delete(adminJwt(), TOKEN), "dependent.open_contract");
    verify(dependents, never()).delete(any());
  }

  @Test
  void underAContractTheSchoolCannotChange() {
    DependentModel model = dependent(true);
    model.setSchoolId(SCHOOL_ID);
    stubOwnershipResolution();
    when(dependents.lockByToken(TOKEN)).thenReturn(Optional.of(model));
    when(dependents.existsInOpenContract(DEPENDENT_ID)).thenReturn(true);

    assertLocked(
        () -> service.update(clientJwt(), TOKEN, schoolOnly(JsonNullable.of("other-school"))),
        "dependent.open_contract");
    assertThat(model.getSchoolId()).isEqualTo(SCHOOL_ID);
    verify(dependents, never()).save(any());
    verifyNoInteractions(schools);
  }

  @Test
  void underAContractTheAddressCannotChangeNorBeCleared() {
    stubOwnershipResolution();
    when(dependents.lockByToken(TOKEN)).thenReturn(Optional.of(dependent(true)));
    when(dependents.existsInOpenContract(DEPENDENT_ID)).thenReturn(true);

    assertLocked(
        () -> service.update(clientJwt(), TOKEN, addressOnly(JsonNullable.of(addressRequest()))),
        "dependent.open_contract");
    assertLocked(
        () -> service.update(clientJwt(), TOKEN, addressOnly(JsonNullable.of(null))),
        "dependent.open_contract");
    verify(dependents, never()).save(any());
    verifyNoInteractions(addressService);
  }

  @Test
  void underAContractTheNameStillChangesWithoutLookingAtTheContract() {
    DependentModel model = dependent(true);
    stubOwnershipResolution();
    stubResponseMapping();
    when(dependents.lockByToken(TOKEN)).thenReturn(Optional.of(model));
    when(dependents.save(model)).thenReturn(model);

    service.update(clientJwt(), TOKEN, nameOnly("Lucas Souza"));

    assertThat(model.getName()).isEqualTo("Lucas Souza");
    verify(dependents, never()).existsInOpenContract(any());
  }

  @Test
  void underAContractTheSchoolDaysCannotBeReplaced() {
    stubOwnershipResolution();
    when(dependents.lockByToken(TOKEN)).thenReturn(Optional.of(dependent(true)));
    when(dependents.existsInOpenContract(DEPENDENT_ID)).thenReturn(true);

    assertLocked(
        () ->
            service.replaceSchoolDays(
                clientJwt(),
                TOKEN,
                schoolDaysRequest(
                    new DependentSchoolDayRequestDTO(DayOfWeek.MONDAY, SchoolShift.MORNING))),
        "dependent.open_contract");
    verifyNoInteractions(schoolDays);
  }

  @Test
  void restoringIsNeverLocked() {
    DependentModel restored = dependent(false);
    stubOwnershipResolution();
    stubResponseMapping();
    when(dependents.findByToken(TOKEN))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(restored));
    when(dependents.findClientIdOfDeletedByToken(TOKEN)).thenReturn(Optional.of(CLIENT_ID));

    service.restore(clientJwt(), TOKEN);

    verify(dependents).restoreByToken(TOKEN);
    verify(dependents, never()).existsInOpenContract(any());
    verify(dependents, never()).existsInOpenServiceRequest(any());
  }

  private void assertLocked(Runnable call, String key) {
    assertThatThrownBy(() -> call.run())
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> {
              assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
              assertThat(e.getReason()).isEqualTo(key);
              assertThat(e.getBody().getProperties()).containsEntry("code", key);
            });
  }

  private Jwt adminJwt() {
    Jwt jwt = mock(Jwt.class);
    lenient().when(jwt.getClaim("roles")).thenReturn(List.of("ROLE_ADMIN"));
    return jwt;
  }

  private DependentUpdateDTO addressOnly(JsonNullable<DependentAddressRequestDTO> address) {
    return patch(
        JsonNullable.undefined(),
        JsonNullable.undefined(),
        JsonNullable.undefined(),
        JsonNullable.undefined(),
        JsonNullable.undefined(),
        JsonNullable.undefined(),
        JsonNullable.undefined(),
        JsonNullable.undefined(),
        address,
        JsonNullable.undefined());
  }

  private DependentSchoolDaysRequestDTO schoolDaysRequest(DependentSchoolDayRequestDTO... days) {
    return new DependentSchoolDaysRequestDTO(List.of(days));
  }

  private DependentSchoolDayModel schoolDay(DayOfWeek weekday, SchoolShift schoolShift) {
    DependentSchoolDayModel day = new DependentSchoolDayModel();
    day.setDependentId(DEPENDENT_ID);
    day.setWeekday(weekday);
    day.setSchoolShift(schoolShift);
    return day;
  }

  private void stubSchoolDayMapping() {
    when(mapper.toSchoolDayModel(eq(DEPENDENT_ID), any()))
        .thenAnswer(
            call -> {
              DependentSchoolDayRequestDTO day = call.getArgument(1);
              return schoolDay(day.weekday(), day.schoolShift());
            });
  }
}
