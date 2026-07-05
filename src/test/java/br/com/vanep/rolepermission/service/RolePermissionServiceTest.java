package br.com.vanep.rolepermission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.rolepermission.dto.RolePermissionCreateRequestDTO;
import br.com.vanep.rolepermission.dto.RolePermissionResponseDTO;
import br.com.vanep.rolepermission.dto.RolePermissionUpdateRequestDTO;
import br.com.vanep.rolepermission.mapper.RolePermissionMapper;
import br.com.vanep.rolepermission.model.RolePermissionModel;
import br.com.vanep.rolepermission.repository.RolePermissionRepository;
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
class RolePermissionServiceTest {

  @Mock private RolePermissionRepository repository;
  @Mock private RolePermissionMapper mapper;
  @Mock private MessageSource messages;

  private RolePermissionService service;

  @BeforeEach
  void setUp() {
    service = new RolePermissionService(repository, mapper, messages);
  }

  private RolePermissionModel bundleWithToken(String token) {
    RolePermissionModel bundle = new RolePermissionModel();
    bundle.setToken(token);
    bundle.setName("ADMIN");
    bundle.setPermissions(List.of("list_roles"));
    return bundle;
  }

  @Test
  void findAllReturnsPagedResponses() {
    RolePermissionModel bundle = bundleWithToken("abc");
    RolePermissionResponseDTO response =
        new RolePermissionResponseDTO("abc", "ADMIN", List.of("list_roles"), null);
    when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(bundle)));
    when(mapper.toResponse(bundle)).thenReturn(response);

    var result = service.findAll(Pageable.unpaged());

    assertThat(result.getContent()).containsExactly(response);
  }

  @Test
  void findByTokenReturnsResponse() {
    RolePermissionModel bundle = bundleWithToken("tok");
    RolePermissionResponseDTO response =
        new RolePermissionResponseDTO("tok", "ADMIN", List.of("list_roles"), null);
    when(repository.findByToken("tok")).thenReturn(Optional.of(bundle));
    when(mapper.toResponse(bundle)).thenReturn(response);

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
  void createPersistsBundleWithValidPermissions() {
    RolePermissionCreateRequestDTO req =
        new RolePermissionCreateRequestDTO("ADMIN", List.of("list_roles"));
    RolePermissionModel saved = bundleWithToken("tok");
    RolePermissionResponseDTO response =
        new RolePermissionResponseDTO("tok", "ADMIN", List.of("list_roles"), null);
    when(repository.existsByName("ADMIN")).thenReturn(false);
    when(repository.save(any(RolePermissionModel.class))).thenReturn(saved);
    when(mapper.toResponse(saved)).thenReturn(response);

    RolePermissionResponseDTO result = service.create(req);

    assertThat(result).isEqualTo(response);
    verify(repository).save(any(RolePermissionModel.class));
  }

  @Test
  void createRejectsDuplicateName() {
    RolePermissionCreateRequestDTO req =
        new RolePermissionCreateRequestDTO("ADMIN", List.of("list_roles"));
    when(repository.existsByName("ADMIN")).thenReturn(true);

    assertThatThrownBy(() -> service.create(req))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("400");
    verify(repository, org.mockito.Mockito.never()).save(any());
  }

  @Test
  void updatePersistsFields() {
    RolePermissionModel bundle = bundleWithToken("tok");
    RolePermissionResponseDTO response =
        new RolePermissionResponseDTO("tok", "Updated", List.of("delete_role"), null);
    when(repository.findByToken("tok")).thenReturn(Optional.of(bundle));
    when(repository.existsByNameAndTokenNot("Updated", "tok")).thenReturn(false);
    when(repository.save(bundle)).thenReturn(bundle);
    when(mapper.toResponse(bundle)).thenReturn(response);

    RolePermissionResponseDTO result =
        service.update(
            "tok", new RolePermissionUpdateRequestDTO("Updated", List.of("delete_role")));

    assertThat(result).isEqualTo(response);
    assertThat(bundle.getName()).isEqualTo("Updated");
    assertThat(bundle.getPermissions()).containsExactly("delete_role");
  }

  @Test
  void updateRejectsDuplicateName() {
    RolePermissionModel bundle = bundleWithToken("tok");
    when(repository.findByToken("tok")).thenReturn(Optional.of(bundle));
    when(repository.existsByNameAndTokenNot("Other", "tok")).thenReturn(true);

    assertThatThrownBy(
            () ->
                service.update(
                    "tok", new RolePermissionUpdateRequestDTO("Other", List.of("list_roles"))))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("400");
  }

  @Test
  void updateThrows404WhenNotFound() {
    when(repository.findByToken("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.update(
                    "missing", new RolePermissionUpdateRequestDTO("x", List.of("list_roles"))))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }

  @Test
  void deleteSoftDeletesBundle() {
    RolePermissionModel bundle = bundleWithToken("tok");
    when(repository.findByToken("tok")).thenReturn(Optional.of(bundle));

    service.delete("tok");

    verify(repository).delete(bundle);
  }

  @Test
  void deleteThrows404WhenNotFound() {
    when(repository.findByToken("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.delete("missing"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }
}
