package br.com.vanep.client.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.client.Client;
import br.com.vanep.client.ClientRepository;
import br.com.vanep.client.dto.ClientResponse;
import br.com.vanep.client.dto.ClientUpdateRequest;
import br.com.vanep.client.mapper.ClientMapper;
import br.com.vanep.user.User;
import br.com.vanep.user.UserType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

  @Mock private ClientRepository repository;
  @Mock private ClientMapper mapper;

  private ClientService service;

  @BeforeEach
  void setUp() {
    service = new ClientService(repository, mapper);
  }

  private Client clientWithToken(String token, String userToken) {
    User user = new User();
    user.setType(UserType.CLIENT);
    user.setName("Test User");
    user.setEmail("test@vanep.com");
    user.setDocument("12345678901");
    user.setToken(userToken);

    Client client = new Client();
    client.setToken(token);
    client.setUser(user);
    return client;
  }

  private Jwt jwtFor(String uid, String... roles) {
    return Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .claim("uid", uid)
        .claim("roles", List.of(roles))
        .build();
  }

  @Test
  void findAllReturnsPagedResponses() {
    Client client = clientWithToken("abc", "user-tok");
    ClientResponse response =
        new ClientResponse("abc", "Test", "t@t.com", null, null, null, true, null);
    when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(client)));
    when(mapper.toResponse(client)).thenReturn(response);

    var result = service.findAll(Pageable.unpaged());

    assertThat(result.getContent()).containsExactly(response);
  }

  @Test
  void findByTokenAllowsAdmin() {
    Client client = clientWithToken("tok", "owner-uid");
    ClientResponse response =
        new ClientResponse("tok", "Name", "e@e.com", null, null, null, true, null);
    when(repository.findByToken("tok")).thenReturn(Optional.of(client));
    when(mapper.toResponse(client)).thenReturn(response);

    Jwt admin = jwtFor("other-uid", "ROLE_ADMIN");
    assertThat(service.findByToken("tok", admin)).isEqualTo(response);
  }

  @Test
  void findByTokenAllowsOwner() {
    Client client = clientWithToken("tok", "owner-uid");
    ClientResponse response =
        new ClientResponse("tok", "Name", "e@e.com", null, null, null, true, null);
    when(repository.findByToken("tok")).thenReturn(Optional.of(client));
    when(mapper.toResponse(client)).thenReturn(response);

    Jwt owner = jwtFor("owner-uid", "ROLE_CLIENT");
    assertThat(service.findByToken("tok", owner)).isEqualTo(response);
  }

  @Test
  void findByTokenDeniesOtherClient() {
    Client client = clientWithToken("tok", "owner-uid");
    when(repository.findByToken("tok")).thenReturn(Optional.of(client));

    Jwt other = jwtFor("other-uid", "ROLE_CLIENT");
    assertThatThrownBy(() -> service.findByToken("tok", other))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("403");
  }

  @Test
  void findByTokenThrows404WhenNotFound() {
    when(repository.findByToken("missing")).thenReturn(Optional.empty());
    Jwt admin = jwtFor("any", "ROLE_ADMIN");

    assertThatThrownBy(() -> service.findByToken("missing", admin))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }

  @Test
  void updateAllowsOwner() {
    Client client = clientWithToken("tok", "owner-uid");
    ClientResponse response =
        new ClientResponse("tok", "Name", "e@e.com", "photo.jpg", null, 1L, true, null);
    when(repository.findByToken("tok")).thenReturn(Optional.of(client));
    when(repository.save(client)).thenReturn(client);
    when(mapper.toResponse(client)).thenReturn(response);

    Jwt owner = jwtFor("owner-uid", "ROLE_CLIENT");
    ClientUpdateRequest req = new ClientUpdateRequest("photo.jpg", 1L);
    ClientResponse result = service.update("tok", req, owner);

    assertThat(result).isEqualTo(response);
    assertThat(client.getPhoto()).isEqualTo("photo.jpg");
    assertThat(client.getAddressId()).isEqualTo(1L);
  }

  @Test
  void updateDeniesNonOwner() {
    Client client = clientWithToken("tok", "owner-uid");
    when(repository.findByToken("tok")).thenReturn(Optional.of(client));

    Jwt other = jwtFor("other-uid", "ROLE_CLIENT");
    assertThatThrownBy(() -> service.update("tok", new ClientUpdateRequest(null, null), other))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("403");
  }

  @Test
  void deleteSoftDeletesClient() {
    Client client = clientWithToken("tok", "owner-uid");
    when(repository.findByToken("tok")).thenReturn(Optional.of(client));

    service.delete("tok");

    verify(repository).delete(client);
  }

  @Test
  void deleteThrows404WhenNotFound() {
    when(repository.findByToken("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.delete("missing"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }
}
