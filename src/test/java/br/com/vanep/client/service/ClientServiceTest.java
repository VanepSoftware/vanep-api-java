package br.com.vanep.client.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.client.dto.ClientResponseDTO;
import br.com.vanep.client.dto.ClientUpdateRequestDTO;
import br.com.vanep.client.mapper.ClientMapper;
import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.UserType;
import br.com.vanep.user.model.UserModel;
import java.math.BigDecimal;
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
  @Mock private UserRepository users;
  @Mock private ClientMapper mapper;

  private ClientService service;

  @BeforeEach
  void setUp() {
    service = new ClientService(repository, users, mapper);
  }

  private ClientModel clientWithToken(String token) {
    UserModel user = new UserModel();
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
    when(mapper.toResponse(client)).thenReturn(response);

    var result = service.findAll(Pageable.unpaged());

    assertThat(result.getContent()).containsExactly(response);
  }

  @Test
  void findByTokenReturnsResponse() {
    ClientModel client = clientWithToken("tok");
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
    ClientModel client = clientWithToken("tok");
    ClientResponseDTO response =
        new ClientResponseDTO("tok", "Name", "e@e.com", "photo.jpg", null, null, true, null);
    when(repository.findByToken("tok")).thenReturn(Optional.of(client));
    when(repository.save(client)).thenReturn(client);
    when(mapper.toResponse(client)).thenReturn(response);

    ClientUpdateRequestDTO req =
        new ClientUpdateRequestDTO(null, null, "photo.jpg", "addr-token-123", null, null);
    ClientResponseDTO result = service.update("tok", req);

    assertThat(result).isEqualTo(response);
    assertThat(client.getPhoto()).isEqualTo("photo.jpg");
    assertThat(client.getUser().getName()).isEqualTo("Test User");
  }

  @Test
  void updatePersistsUserNameWhenProvided() {
    ClientModel client = clientWithToken("tok");
    ClientResponseDTO response =
        new ClientResponseDTO("tok", "Novo Nome", "e@e.com", null, null, null, true, null);
    when(repository.findByToken("tok")).thenReturn(Optional.of(client));
    when(repository.save(client)).thenReturn(client);
    when(mapper.toResponse(client)).thenReturn(response);

    ClientResponseDTO result =
        service.update(
            "tok", new ClientUpdateRequestDTO("Novo Nome", null, null, null, null, null));

    assertThat(result).isEqualTo(response);
    assertThat(client.getUser().getName()).isEqualTo("Novo Nome");
  }

  @Test
  void updateKeepsUserNameWhenBlank() {
    ClientModel client = clientWithToken("tok");
    when(repository.findByToken("tok")).thenReturn(Optional.of(client));
    when(repository.save(client)).thenReturn(client);
    when(mapper.toResponse(client)).thenReturn(null);

    service.update("tok", new ClientUpdateRequestDTO("   ", null, null, null, null, null));

    assertThat(client.getUser().getName()).isEqualTo("Test User");
  }

  @Test
  void updatePersistsEmailRatingAndActive() {
    ClientModel client = clientWithToken("tok");
    when(repository.findByToken("tok")).thenReturn(Optional.of(client));
    when(repository.save(client)).thenReturn(client);
    when(users.existsByEmail("novo@vanep.com")).thenReturn(false);
    when(mapper.toResponse(client)).thenReturn(null);

    service.update(
        "tok",
        new ClientUpdateRequestDTO(
            null, "novo@vanep.com", null, null, new BigDecimal("4.5"), false));

    assertThat(client.getUser().getEmail()).isEqualTo("novo@vanep.com");
    assertThat(client.getRating()).isEqualByComparingTo("4.5");
    assertThat(client.isActive()).isFalse();
  }

  @Test
  void updateKeepsSameEmailWithoutConflictCheck() {
    ClientModel client = clientWithToken("tok");
    when(repository.findByToken("tok")).thenReturn(Optional.of(client));
    when(repository.save(client)).thenReturn(client);
    when(mapper.toResponse(client)).thenReturn(null);

    service.update(
        "tok", new ClientUpdateRequestDTO(null, "TEST@vanep.com", null, null, null, null));

    assertThat(client.getUser().getEmail()).isEqualTo("TEST@vanep.com");
  }

  @Test
  void updateThrows409WhenEmailBelongsToAnotherUser() {
    ClientModel client = clientWithToken("tok");
    when(repository.findByToken("tok")).thenReturn(Optional.of(client));
    when(users.existsByEmail("usado@vanep.com")).thenReturn(true);

    assertThatThrownBy(
            () ->
                service.update(
                    "tok",
                    new ClientUpdateRequestDTO(null, "usado@vanep.com", null, null, null, null)))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("409");
  }

  @Test
  void updateThrows404WhenNotFound() {
    when(repository.findByToken("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.update(
                    "missing", new ClientUpdateRequestDTO(null, null, null, null, null, null)))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
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
        .hasMessageContaining("404");
  }
}
