package br.com.vanep.dependent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.address.dto.AddressRequestDTO;
import br.com.vanep.address.dto.AddressResponseDTO;
import br.com.vanep.address.service.AddressService;
import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.dependent.dto.DependentCreateDTO;
import br.com.vanep.dependent.dto.DependentResponseDTO;
import br.com.vanep.dependent.dto.DependentUpdateDTO;
import br.com.vanep.dependent.enums.Shift;
import br.com.vanep.dependent.mapper.DependentMapper;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.dependent.repository.DependentRepository;
import br.com.vanep.user.enums.Gender;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
  private static final String TOKEN = "tok";

  @Mock private DependentRepository dependents;
  @Mock private ClientRepository clients;
  @Mock private UserRepository users;
  @Mock private DependentMapper mapper;
  @Mock private AddressService addressService;
  @Mock private MessageSource messages;

  private DependentService service;

  @BeforeEach
  void setUp() {
    service = new DependentService(dependents, clients, users, mapper, addressService, messages);
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
        .when(mapper.toResponse(any(DependentModel.class), eq("ctok"), isNull(), any()))
        .thenReturn(response(null));
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
        Shift.MORNING,
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

  private AddressRequestDTO addressRequest() {
    return new AddressRequestDTO(
        "city-campinas", "13015904", "Rua Barão de Jaguara", "1481", null, "Centro");
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
    model.setShift(Shift.MORNING);
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
      JsonNullable<Shift> shift,
      JsonNullable<AddressRequestDTO> address,
      JsonNullable<String> schoolToken) {
    return new DependentUpdateDTO(
        name,
        birthDate,
        gender,
        document,
        phone,
        email,
        isSelf,
        isDefault,
        shift,
        address,
        schoolToken);
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
  void createWithSchoolTokenThrows400() {
    DependentCreateDTO dto = new DependentCreateDTO();
    dto.setName("Kid");
    dto.setSchoolToken("school-tok");

    stubOwnershipResolution();

    assertThatThrownBy(() -> service.create(clientJwt(), dto))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    verify(dependents, never()).save(any());
  }

  @Test
  void deletingDefaultPromotesRemainingWhenExactlyOneLeft() {
    DependentModel toDelete = dependent(true);
    DependentModel remaining = dependent(false);
    remaining.setToken("other");

    stubOwnershipResolution();
    when(dependents.findByToken(TOKEN)).thenReturn(Optional.of(toDelete));
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
    when(dependents.findByToken(TOKEN)).thenReturn(Optional.of(toDelete));
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

    assertThat(result.address()).isNull();
    verify(addressService, never()).upsertForDependent(any(), any());
  }

  @Test
  void updateNameOnlyDoesNotTouchAddressOrNullableScalars() {
    DependentModel model = dependent(true);
    model.setAddressId(10L);

    stubOwnershipResolution();
    stubResponseMapping();
    when(dependents.findByToken(TOKEN)).thenReturn(Optional.of(model));
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
    when(dependents.findByToken(TOKEN)).thenReturn(Optional.of(model));
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
    when(dependents.findByToken(TOKEN)).thenReturn(Optional.of(model));
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
            JsonNullable.undefined(),
            JsonNullable.of(null),
            JsonNullable.undefined()));

    verify(addressService).clearForDependent(DEPENDENT_ID);
    verify(addressService, never()).upsertForDependent(any(), any());
  }

  @Test
  void updateNestedAddressUpsertsOwnedRow() {
    DependentModel model = dependent(true);
    AddressRequestDTO request = addressRequest();

    stubOwnershipResolution();
    stubResponseMapping();
    when(dependents.findByToken(TOKEN)).thenReturn(Optional.of(model));
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
            JsonNullable.undefined(),
            JsonNullable.of(request),
            JsonNullable.undefined()));

    verify(addressService).upsertForDependent(DEPENDENT_ID, request);
  }

  @Test
  void updatePresentSchoolTokenThrows400() {
    DependentModel model = dependent(true);

    stubOwnershipResolution();
    when(dependents.findByToken(TOKEN)).thenReturn(Optional.of(model));

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
                        JsonNullable.undefined(),
                        JsonNullable.undefined(),
                        JsonNullable.undefined(),
                        JsonNullable.of("school-tok"))))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    verify(dependents, never()).save(any());
  }

  @Test
  void updatePresentNullSchoolTokenThrows400() {
    DependentModel model = dependent(true);

    stubOwnershipResolution();
    when(dependents.findByToken(TOKEN)).thenReturn(Optional.of(model));

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
                        JsonNullable.undefined(),
                        JsonNullable.undefined(),
                        JsonNullable.undefined(),
                        JsonNullable.of(null))))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void updatePresentBlankNameThrows400AndLeavesStoredName() {
    DependentModel model = dependent(true);

    stubOwnershipResolution();
    when(dependents.findByToken(TOKEN)).thenReturn(Optional.of(model));

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
    when(dependents.findByToken(TOKEN)).thenReturn(Optional.of(model));

    assertThatThrownBy(() -> service.update(clientJwt(), TOKEN, nameOnly(null)))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void updatePresentNullIsDefaultThrows400() {
    DependentModel model = dependent(true);

    stubOwnershipResolution();
    when(dependents.findByToken(TOKEN)).thenReturn(Optional.of(model));

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
    when(dependents.findByToken(TOKEN)).thenReturn(Optional.of(model));
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
    when(dependents.findByToken(TOKEN)).thenReturn(Optional.of(model));
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
    when(dependents.findByToken(TOKEN)).thenReturn(Optional.of(model));
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
            JsonNullable.undefined(),
            JsonNullable.undefined()));

    assertThat(model.getDocument()).isEqualTo("11111111111");
    verify(dependents, never()).existsByDocumentAndTokenNot(any(), any());
  }
}
