package br.com.vanep.role.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.role.Role;
import br.com.vanep.role.dto.RoleCreateRequestDTO;
import br.com.vanep.role.dto.RoleResponseDTO;
import br.com.vanep.role.dto.RoleUpdateRequestDTO;
import br.com.vanep.role.mapper.RoleMapper;
import br.com.vanep.role.repository.RoleRepository;
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
class RoleServiceTest {

  @Mock private RoleRepository repository;
  @Mock private RoleMapper mapper;

  private RoleService service;

  @BeforeEach
  void setUp() {
    service = new RoleService(repository, mapper);
  }

  private Role roleWithToken(String token) {
    Role role = new Role();
    role.setToken(token);
    role.setName("Admin");
    role.setDescription("Administrator role");
    return role;
  }

  @Test
  void findAllReturnsPagedResponses() {
    Role role = roleWithToken("abc");
    RoleResponseDTO response = new RoleResponseDTO("abc", "Admin", "Administrator role", null);
    when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(role)));
    when(mapper.toResponse(role)).thenReturn(response);

    var result = service.findAll(Pageable.unpaged());

    assertThat(result.getContent()).containsExactly(response);
  }

  @Test
  void findByTokenReturnsResponse() {
    Role role = roleWithToken("tok");
    RoleResponseDTO response = new RoleResponseDTO("tok", "Admin", "desc", null);
    when(repository.findByToken("tok")).thenReturn(Optional.of(role));
    when(mapper.toResponse(role)).thenReturn(response);

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
  void createPersistsRole() {
    RoleCreateRequestDTO req = new RoleCreateRequestDTO("Admin", "Administrator role");
    Role saved = roleWithToken("tok");
    RoleResponseDTO response = new RoleResponseDTO("tok", "Admin", "Administrator role", null);
    when(repository.save(any(Role.class))).thenReturn(saved);
    when(mapper.toResponse(saved)).thenReturn(response);

    RoleResponseDTO result = service.create(req);

    assertThat(result).isEqualTo(response);
    verify(repository).save(any(Role.class));
  }

  @Test
  void updatePersistsFields() {
    Role role = roleWithToken("tok");
    RoleResponseDTO response = new RoleResponseDTO("tok", "Updated", "new desc", null);
    when(repository.findByToken("tok")).thenReturn(Optional.of(role));
    when(repository.save(role)).thenReturn(role);
    when(mapper.toResponse(role)).thenReturn(response);

    RoleResponseDTO result = service.update("tok", new RoleUpdateRequestDTO("Updated", "new desc"));

    assertThat(result).isEqualTo(response);
    assertThat(role.getName()).isEqualTo("Updated");
    assertThat(role.getDescription()).isEqualTo("new desc");
  }

  @Test
  void updateThrows404WhenNotFound() {
    when(repository.findByToken("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.update("missing", new RoleUpdateRequestDTO("x", "y")))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }

  @Test
  void deleteSoftDeletesRole() {
    Role role = roleWithToken("tok");
    when(repository.findByToken("tok")).thenReturn(Optional.of(role));

    service.delete("tok");

    verify(repository).delete(role);
  }

  @Test
  void deleteThrows404WhenNotFound() {
    when(repository.findByToken("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.delete("missing"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }

  @Test
  void restoreReturnsResponse() {
    Role role = roleWithToken("tok");
    RoleResponseDTO response = new RoleResponseDTO("tok", "Admin", "desc", null);
    when(repository.findDeletedByToken("tok")).thenReturn(Optional.of(role));
    when(repository.findByToken("tok")).thenReturn(Optional.of(role));
    when(mapper.toResponse(role)).thenReturn(response);

    RoleResponseDTO result = service.restore("tok");

    assertThat(result).isEqualTo(response);
    verify(repository).restore("tok");
  }

  @Test
  void restoreThrows404WhenNotFound() {
    when(repository.findDeletedByToken("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.restore("missing"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }
}
