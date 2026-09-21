package br.com.vanep.client.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import br.com.vanep.address.dto.AddressResponseDTO;
import br.com.vanep.address.service.AddressService;
import br.com.vanep.client.dto.ClientMeSummaryResponseDTO;
import br.com.vanep.client.dto.ClientResponseDTO;
import br.com.vanep.client.dto.ClientUpdateRequestDTO;
import br.com.vanep.client.mapper.ClientMapper;
import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.user.dto.UserMeResponseDTO;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import br.com.vanep.user.service.UserService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {
  @Mock private ClientRepository repository;
  @Mock private ClientMapper mapper;
  @Mock private UserService userService;
  @Mock private AddressService addressService;
  @Mock private MessageSource messages;
  @Mock private UserRepository users;

  private ClientService service;

  @BeforeEach
  void setUp() {
    service = new ClientService(repository, users, mapper, userService, addressService, messages);
    lenient().when(messages.getMessage(any(), any(), any())).thenAnswer(inv -> inv.getArgument(0));
  }

  private ClientModel clientWithToken(String token) {
    UserModel user = new UserModel();
    user.setId(1L);
    user.setType(UserType.CLIENT);
    user.setName("Test User");
    user.setEmail("test@vanep.com");
    user.setDocument("12345678901");
    user.setToken("owner-uid");

    ClientModel client = new ClientModel();
    client.setId(7L);
    client.setToken(token);
    client.setUser(user);
    return client;
  }

  private AddressResponseDTO addressResponse(String token) {
    return new AddressResponseDTO(
        token,
        "13015904",
        "Rua Barão de Jaguara",
        "1481",
        "Apto 12",
        null,
        "Centro",
        "city-campinas",
        "Campinas",
        "SP",
        true,
        null);
  }

  private UserMeResponseDTO userMe() {
    return new UserMeResponseDTO(
        "owner-uid",
        "Test User",
        null,
        "test@vanep.com",
        "12345678901",
        null,
        null,
        "CLIENT",
        null,
        null,
        null,
        null,
        null);
  }

  @Test
  void findAllReturnsPagedResponsesWithNestedAddress() {
    ClientModel client = clientWithToken("abc");
    client.getUser().setAddressId(10L);
    AddressResponseDTO address = addressResponse("addr-tok");
    ClientResponseDTO response =
        new ClientResponseDTO("abc", "Test", "t@t.com", null, null, address, true, null);
    when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(client)));
    when(addressService.toResponsesByIds(List.of(10L))).thenReturn(Map.of(10L, address));
    when(mapper.toResponse(client, address)).thenReturn(response);

    var result = service.findAll(Pageable.unpaged());

    assertThat(result.getContent()).containsExactly(response);
    assertThat(result.getContent().get(0).address().token()).isEqualTo("addr-tok");
  }

  @Test
  void findAllReturnsNullAddressWhenClientHasNone() {
    ClientModel client = clientWithToken("abc");
    ClientResponseDTO response =
        new ClientResponseDTO("abc", "Test", "t@t.com", null, null, null, true, null);
    when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(client)));
    when(addressService.toResponsesByIds(List.of())).thenReturn(Map.of());
    when(mapper.toResponse(client, null)).thenReturn(response);

    var result = service.findAll(Pageable.unpaged());

    assertThat(result.getContent().get(0).address()).isNull();
  }

  @Test
  void findByTokenReturnsNestedAddress() {
    ClientModel client = clientWithToken("tok");
    client.getUser().setAddressId(10L);
    AddressResponseDTO address = addressResponse("addr-tok");
    ClientResponseDTO response =
        new ClientResponseDTO("tok", "Name", "e@e.com", null, null, address, true, null);
    when(repository.findByToken("tok")).thenReturn(Optional.of(client));
    when(addressService.toResponseOrNull(10L)).thenReturn(address);
    when(mapper.toResponse(client, address)).thenReturn(response);

    assertThat(service.findByToken("tok")).isEqualTo(response);
    assertThat(service.findByToken("tok").address().street()).isEqualTo("Rua Barão de Jaguara");
  }

  @Test
  void findByTokenThrows404WhenNotFound() {
    when(repository.findByToken("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.findByToken("missing"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
        .isEqualTo(404);
  }

  @Test
  void updatePersistsPhotoWithoutTouchingAddress() {
    ClientModel client = clientWithToken("tok");
    client.getUser().setAddressId(5L);
    AddressResponseDTO address = addressResponse("addr-tok");
    ClientResponseDTO response =
        new ClientResponseDTO("tok", "Name", "e@e.com", "photo.jpg", null, address, true, null);
    when(repository.findByToken("tok")).thenReturn(Optional.of(client));
    when(repository.save(client)).thenReturn(client);
    when(addressService.toResponseOrNull(5L)).thenReturn(address);
    when(mapper.toResponse(client, address)).thenReturn(response);

    ClientResponseDTO result =
        service.update("tok", updateWith(JsonNullable.of("photo.jpg"), null, null, null));

    assertThat(result).isEqualTo(response);
    assertThat(client.getPhoto()).isEqualTo("photo.jpg");
    assertThat(client.getUser().getAddressId()).isEqualTo(5L);
  }

  @Test
  void updateThrows404WhenNotFound() {
    when(repository.findByToken("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.update("missing", updateWith(null, null, null, null)))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
        .isEqualTo(404);
  }

  @Test
  void deleteThrows404WhenNotFound() {
    when(repository.findByToken("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.delete("missing"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
        .isEqualTo(404);
  }

  @Test
  void getMyProfileThrows403WhenUserTypeMismatch() {
    when(userService.requireByTokenAndType("driver-uid", UserType.CLIENT))
        .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "forbidden"));

    assertThatThrownBy(() -> service.getMyProfile("driver-uid"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
        .isEqualTo(403);
  }

  @Test
  void getMyProfileReturnsNullAddressWhenClientHasNone() {
    ClientModel client = clientWithToken("client-tok");
    UserModel user = client.getUser();
    var summary = new ClientMeSummaryResponseDTO("client-tok", null, null, true, userMe(), null);
    when(userService.requireByTokenAndType("owner-uid", UserType.CLIENT)).thenReturn(user);
    when(repository.findByUserId(1L)).thenReturn(Optional.of(client));
    when(userService.toMeResponse(user)).thenReturn(userMe());
    when(addressService.toResponseOrNull(null)).thenReturn(null);
    when(mapper.toMeSummary(client, userMe(), null)).thenReturn(summary);

    var result = service.getMyProfile("owner-uid");

    assertThat(result.token()).isEqualTo("client-tok");
    assertThat(result.address()).isNull();
    assertThat(result.user().email()).isEqualTo("test@vanep.com");
  }

  @Test
  void getMyProfileReturnsNestedAddressWhenLinked() {
    ClientModel client = clientWithToken("client-tok");
    client.getUser().setAddressId(10L);
    UserModel user = client.getUser();
    AddressResponseDTO address = addressResponse("addr-tok");
    var summary = new ClientMeSummaryResponseDTO("client-tok", null, null, true, userMe(), address);
    when(userService.requireByTokenAndType("owner-uid", UserType.CLIENT)).thenReturn(user);
    when(repository.findByUserId(1L)).thenReturn(Optional.of(client));
    when(userService.toMeResponse(user)).thenReturn(userMe());
    when(addressService.toResponseOrNull(10L)).thenReturn(address);
    when(mapper.toMeSummary(client, userMe(), address)).thenReturn(summary);

    var result = service.getMyProfile("owner-uid");

    assertThat(result.address()).isEqualTo(address);
    assertThat(result.address().cityToken()).isEqualTo("city-campinas");
    assertThat(result.address().stateUf()).isEqualTo("SP");
  }

  @Test
  void patchingOnlyPhotoLeavesEveryOtherStoredFieldUnchanged() {
    ClientModel client = clientWith("Ana", "ana@vanep.com", true);
    when(repository.findByToken("tok")).thenReturn(Optional.of(client));
    when(repository.save(any(ClientModel.class))).thenAnswer(call -> call.getArgument(0));

    service.update("tok", updateWith(JsonNullable.of("nova.jpg"), null, null, null));

    assertThat(client.getPhoto()).isEqualTo("nova.jpg");
    assertThat(client.getUser().getName()).isEqualTo("Ana");
    assertThat(client.getUser().getEmail()).isEqualTo("ana@vanep.com");
    assertThat(client.isActive()).isTrue();
  }

  @Test
  void adminCanRenameTheClient() {
    ClientModel client = clientWith("Ana", "ana@vanep.com", true);
    when(repository.findByToken("tok")).thenReturn(Optional.of(client));
    when(repository.save(any(ClientModel.class))).thenAnswer(call -> call.getArgument(0));

    service.update("tok", updateWith(null, JsonNullable.of("Ana Souza"), null, null));

    assertThat(client.getUser().getName()).isEqualTo("Ana Souza");
  }

  @Test
  void deactivatingTheClientIsPersisted() {
    ClientModel client = clientWith("Ana", "ana@vanep.com", true);
    when(repository.findByToken("tok")).thenReturn(Optional.of(client));
    when(repository.save(any(ClientModel.class))).thenAnswer(call -> call.getArgument(0));

    service.update("tok", updateWith(null, null, null, JsonNullable.of(false)));

    assertThat(client.isActive()).isFalse();
  }

  @Test
  void aBlankNameIsRefused() {
    ClientModel client = clientWith("Ana", "ana@vanep.com", true);
    when(repository.findByToken("tok")).thenReturn(Optional.of(client));

    assertThatThrownBy(
            () -> service.update("tok", updateWith(null, JsonNullable.of("  "), null, null)))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("400");
  }

  @Test
  void clearingNameExplicitlyIsRefused() {
    ClientModel client = clientWith("Ana", "ana@vanep.com", true);
    when(repository.findByToken("tok")).thenReturn(Optional.of(client));

    assertThatThrownBy(
            () -> service.update("tok", updateWith(null, JsonNullable.of(null), null, null)))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("400");
  }

  @Test
  void clearingActiveExplicitlyIsRefused() {
    ClientModel client = clientWith("Ana", "ana@vanep.com", true);
    when(repository.findByToken("tok")).thenReturn(Optional.of(client));

    assertThatThrownBy(
            () -> service.update("tok", updateWith(null, null, null, JsonNullable.of(null))))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("400");
  }

  @Test
  void anEmailAlreadyTakenByAnotherUserIsRefused() {
    ClientModel client = clientWith("Ana", "ana@vanep.com", true);
    when(repository.findByToken("tok")).thenReturn(Optional.of(client));
    when(users.existsByEmail("ocupado@vanep.com")).thenReturn(true);

    assertThatThrownBy(
            () ->
                service.update(
                    "tok", updateWith(null, null, JsonNullable.of("ocupado@vanep.com"), null)))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("409");
  }

  @Test
  void resendingTheSameEmailIsNotADuplicate() {
    ClientModel client = clientWith("Ana", "ana@vanep.com", true);
    when(repository.findByToken("tok")).thenReturn(Optional.of(client));
    when(repository.save(any(ClientModel.class))).thenAnswer(call -> call.getArgument(0));

    service.update("tok", updateWith(null, null, JsonNullable.of("ana@vanep.com"), null));

    assertThat(client.getUser().getEmail()).isEqualTo("ana@vanep.com");
  }

  private static ClientModel clientWith(String name, String email, boolean active) {
    UserModel user = new UserModel();
    user.setName(name);
    user.setEmail(email);

    ClientModel client = new ClientModel();
    client.setUser(user);
    client.setActive(active);
    return client;
  }

  private static ClientUpdateRequestDTO updateWith(
      JsonNullable<String> photo,
      JsonNullable<String> name,
      JsonNullable<String> email,
      JsonNullable<Boolean> active) {
    return new ClientUpdateRequestDTO(name, email, photo, null, active);
  }
}
