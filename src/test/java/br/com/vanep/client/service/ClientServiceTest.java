package br.com.vanep.client.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.address.service.AddressService;
import br.com.vanep.client.dto.ClientResponseDTO;
import br.com.vanep.client.dto.ClientUpdateRequestDTO;
import br.com.vanep.client.mapper.ClientMapper;
import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.user.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.service.UserService;
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
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

  @Mock private ClientRepository repository;
  @Mock private ClientMapper mapper;
  @Mock private UserService userService;
  @Mock private AddressService addressService;
  @Mock private MessageSource messages;

  private ClientService service;

  @BeforeEach
  void setUp() {
    service = new ClientService(repository, mapper, userService, addressService, messages);
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
    client.setToken(token);
    client.setUser(user);
    return client;
  }

  @Test
  void findAllReturnsPagedResponses() {
    ClientModel client = clientWithToken("abc");
    ClientResponseDTO response =
        new ClientResponseDTO("abc", "Test", "t@t.com", null, null, null, true, null);
    when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(client)));
    when(addressService.resolveAddressToken(null)).thenReturn(null);
    when(mapper.toResponse(client, null)).thenReturn(response);

    var result = service.findAll(Pageable.unpaged());

    assertThat(result.getContent()).containsExactly(response);
  }

  @Test
  void findByTokenReturnsResponse() {
    ClientModel client = clientWithToken("tok");
    ClientResponseDTO response =
        new ClientResponseDTO("tok", "Name", "e@e.com", null, null, null, true, null);
    when(repository.findByToken("tok")).thenReturn(Optional.of(client));
    when(addressService.resolveAddressToken(null)).thenReturn(null);
    when(mapper.toResponse(client, null)).thenReturn(response);

    assertThat(service.findByToken("tok")).isEqualTo(response);
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
  void updatePersistsFields() {
    ClientModel client = clientWithToken("tok");
    ClientResponseDTO response =
        new ClientResponseDTO(
            "tok", "Name", "e@e.com", "photo.jpg", null, "addr-token", true, null);
    when(repository.findByToken("tok")).thenReturn(Optional.of(client));
    when(addressService.resolveAddressId("addr-token-123")).thenReturn(99L);
    when(repository.save(client)).thenReturn(client);
    when(addressService.resolveAddressToken(99L)).thenReturn("addr-token");
    when(mapper.toResponse(client, "addr-token")).thenReturn(response);

    ClientUpdateRequestDTO req = new ClientUpdateRequestDTO("photo.jpg", "addr-token-123");
    ClientResponseDTO result = service.update("tok", req);

    assertThat(result).isEqualTo(response);
    assertThat(client.getPhoto()).isEqualTo("photo.jpg");
    assertThat(client.getAddressId()).isEqualTo(99L);
  }

  @Test
  void updateThrows404WhenNotFound() {
    when(repository.findByToken("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.update("missing", new ClientUpdateRequestDTO(null, (String) null)))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
        .isEqualTo(404);
  }

  @Test
  void deleteSoftDeletesClient() {
    ClientModel client = clientWithToken("tok");
    when(repository.findByToken("tok")).thenReturn(Optional.of(client));

    service.delete("tok");

    verify(repository).delete(client);
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
    UserModel driverUser = new UserModel();
    driverUser.setId(2L);
    driverUser.setType(UserType.DRIVER);
    when(userService.requireByTokenAndType("driver-uid", UserType.CLIENT))
        .thenThrow(
            new ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "forbidden"));

    assertThatThrownBy(() -> service.getMyProfile("driver-uid"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
        .isEqualTo(403);
  }

  @Test
  void getMyProfileReturnsSummaryForClient() {
    ClientModel client = clientWithToken("client-tok");
    UserModel user = client.getUser();
    var userMe =
        new br.com.vanep.user.dto.UserMeResponseDTO(
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
            null);
    var summary =
        new br.com.vanep.client.dto.ClientMeSummaryResponseDTO(
            "client-tok", null, null, true, userMe);
    when(userService.requireByTokenAndType("owner-uid", UserType.CLIENT)).thenReturn(user);
    when(repository.findByUserId(1L)).thenReturn(Optional.of(client));
    when(userService.toMeResponse(user)).thenReturn(userMe);
    when(mapper.toMeSummary(client, userMe)).thenReturn(summary);

    var result = service.getMyProfile("owner-uid");

    assertThat(result.token()).isEqualTo("client-tok");
    assertThat(result.user().email()).isEqualTo("test@vanep.com");
  }
}
