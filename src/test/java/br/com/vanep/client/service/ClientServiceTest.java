package br.com.vanep.client.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.client.Client;
import br.com.vanep.client.ClientRepository;
import br.com.vanep.client.dto.ClientResponseDTO;
import br.com.vanep.client.dto.ClientUpdateRequestDTO;
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

  private Client clientWithToken(String token) {
    User user = new User();
    user.setType(UserType.CLIENT);
    user.setName("Test User");
    user.setEmail("test@vanep.com");
    user.setDocument("12345678901");
    user.setToken("owner-uid");

    Client client = new Client();
    client.setToken(token);
    client.setUser(user);
    return client;
  }

  @Test
  void findAllReturnsPagedResponses() {
    Client client = clientWithToken("abc");
    ClientResponseDTO response =
        new ClientResponseDTO("abc", "Test", "t@t.com", null, null, null, true, null);
    when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(client)));
    when(mapper.toResponse(client)).thenReturn(response);

    var result = service.findAll(Pageable.unpaged());

    assertThat(result.getContent()).containsExactly(response);
  }

  @Test
  void findByTokenReturnsResponse() {
    Client client = clientWithToken("tok");
    ClientResponseDTO response =
        new ClientResponseDTO("tok", "Name", "e@e.com", null, null, null, true, null);
    when(repository.findByToken("tok")).thenReturn(Optional.of(client));
    when(mapper.toResponse(client)).thenReturn(response);

    assertThat(service.findByToken("tok")).isEqualTo(response);
  }

  @Test
  void findByTokenThrows404WhenNotFound() {
    when(repository.findByToken("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.findByToken("missing"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }

  @Test
  void updatePersistsFields() {
    Client client = clientWithToken("tok");
    ClientResponseDTO response =
        new ClientResponseDTO("tok", "Name", "e@e.com", "photo.jpg", null, null, true, null);
    when(repository.findByToken("tok")).thenReturn(Optional.of(client));
    when(repository.save(client)).thenReturn(client);
    when(mapper.toResponse(client)).thenReturn(response);

    ClientUpdateRequestDTO req = new ClientUpdateRequestDTO("photo.jpg", "addr-token-123");
    ClientResponseDTO result = service.update("tok", req);

    assertThat(result).isEqualTo(response);
    assertThat(client.getPhoto()).isEqualTo("photo.jpg");
  }

  @Test
  void updateThrows404WhenNotFound() {
    when(repository.findByToken("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.update("missing", new ClientUpdateRequestDTO(null, (String) null)))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }

  @Test
  void deleteSoftDeletesClient() {
    Client client = clientWithToken("tok");
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
