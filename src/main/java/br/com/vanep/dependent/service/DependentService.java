package br.com.vanep.dependent.service;

import br.com.vanep.client.Client;
import br.com.vanep.client.ClientRepository;
import br.com.vanep.dependent.dto.DependentCreateDTO;
import br.com.vanep.dependent.dto.DependentResponseDTO;
import br.com.vanep.dependent.dto.DependentUpdateDTO;
import br.com.vanep.dependent.entity.DependentEntity;
import br.com.vanep.dependent.mapper.DependentMapper;
import br.com.vanep.dependent.repository.DependentRepository;
import br.com.vanep.user.User;
import br.com.vanep.user.UserRepository;
import java.util.Collection;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DependentService {

  private final DependentRepository dependents;
  private final ClientRepository clients;
  private final UserRepository users;
  private final DependentMapper mapper;

  public DependentService(
      DependentRepository dependents,
      ClientRepository clients,
      UserRepository users,
      DependentMapper mapper) {
    this.dependents = dependents;
    this.clients = clients;
    this.users = users;
    this.mapper = mapper;
  }

  @Transactional
  public DependentResponseDTO create(Jwt jwt, DependentCreateDTO dto) {
    Long clientId = resolveClientIdForCreate(jwt, dto);
    assertDocumentAvailable(dto.getDocument(), null);

    DependentEntity entity = mapper.toEntity(dto, clientId);
    applyDefaultOnCreate(entity, dto, clientId);

    DependentEntity saved = dependents.save(entity);
    return mapper.toResponse(saved);
  }

  @Transactional(readOnly = true)
  public List<DependentResponseDTO> list(Jwt jwt) {
    if (isAdmin(jwt)) {
      return dependents.findAll().stream().map(mapper::toResponse).toList();
    }
    Long clientId = resolveClientIdForClient(jwt);
    return dependents.findByClientId(clientId).stream().map(mapper::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public DependentResponseDTO getByToken(Jwt jwt, String token) {
    DependentEntity entity = findActiveForAccess(jwt, token);
    return mapper.toResponse(entity);
  }

  @Transactional
  public DependentResponseDTO update(Jwt jwt, String token, DependentUpdateDTO dto) {
    DependentEntity entity = findActiveForAccess(jwt, token);
    if (dto.getDocument() != null) {
      assertDocumentAvailable(dto.getDocument(), token);
    }

    mapper.applyUpdate(dto, entity);
    if (Boolean.TRUE.equals(dto.getIsDefault())) {
      clearOtherDefaults(entity.getClientId(), entity.getToken());
      entity.setDefaultDependent(true);
    } else if (Boolean.FALSE.equals(dto.getIsDefault())) {
      entity.setDefaultDependent(false);
    }

    DependentEntity saved = dependents.save(entity);
    return mapper.toResponse(saved);
  }

  @Transactional
  public void delete(Jwt jwt, String token) {
    DependentEntity entity = findActiveForAccess(jwt, token);
    boolean wasDefault = entity.isDefaultDependent();
    Long clientId = entity.getClientId();

    dependents.delete(entity);
    promoteDefaultAfterDelete(clientId, wasDefault);
  }

  @Transactional
  public DependentResponseDTO restore(Jwt jwt, String token) {
    if (dependents.findByToken(token).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Dependente já está ativo.");
    }

    Long clientId = dependents.findClientIdOfDeletedByToken(token).orElseThrow(this::notFound);
    assertOwnership(jwt, clientId);

    dependents.restoreByToken(token);
    DependentEntity restored = dependents.findByToken(token).orElseThrow(this::notFound);
    return mapper.toResponse(restored);
  }

  private DependentEntity findActiveForAccess(Jwt jwt, String token) {
    DependentEntity entity = dependents.findByToken(token).orElseThrow(this::notFound);
    assertOwnership(jwt, entity.getClientId());
    return entity;
  }

  private void promoteDefaultAfterDelete(Long clientId, boolean wasDefault) {
    if (!wasDefault) {
      return;
    }
    List<DependentEntity> remaining = dependents.findByClientId(clientId);
    if (remaining.size() == 1) {
      DependentEntity only = remaining.getFirst();
      only.setDefaultDependent(true);
      dependents.save(only);
    }
  }

  private void applyDefaultOnCreate(DependentEntity entity, DependentCreateDTO dto, Long clientId) {
    long activeCount = dependents.countByClientId(clientId);
    if (activeCount == 0) {
      entity.setDefaultDependent(true);
      return;
    }
    if (Boolean.TRUE.equals(dto.getIsDefault())) {
      clearOtherDefaults(clientId, null);
      entity.setDefaultDependent(true);
    } else {
      entity.setDefaultDependent(false);
    }
  }

  private void clearOtherDefaults(Long clientId, String excludeToken) {
    dependents.findByClientId(clientId).stream()
        .filter(dependent -> excludeToken == null || !dependent.getToken().equals(excludeToken))
        .filter(DependentEntity::isDefaultDependent)
        .forEach(
            dependent -> {
              dependent.setDefaultDependent(false);
              dependents.save(dependent);
            });
  }

  private void assertDocumentAvailable(String document, String excludeToken) {
    if (!StringUtils.hasText(document)) {
      return;
    }
    boolean duplicate =
        excludeToken == null
            ? dependents.existsByDocument(document)
            : dependents.existsByDocumentAndTokenNot(document, excludeToken);
    if (duplicate) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Documento já cadastrado para outro dependente.");
    }
  }

  private Long resolveClientIdForCreate(Jwt jwt, DependentCreateDTO dto) {
    if (isAdmin(jwt)) {
      if (dto.getClientId() == null) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "clientId é obrigatório para administradores.");
      }
      return dto.getClientId();
    }
    return resolveClientIdForClient(jwt);
  }

  private Long resolveClientIdForClient(Jwt jwt) {
    User user = requireUser(jwt);
    return clients
        .findByUserId(user.getId())
        .map(Client::getId)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Perfil de cliente não encontrado."));
  }

  private User requireUser(Jwt jwt) {
    return users
        .findByEmail(jwt.getSubject())
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conta não encontrada."));
  }

  private void assertOwnership(Jwt jwt, Long clientId) {
    if (isAdmin(jwt)) {
      return;
    }
    Long actorClientId = resolveClientIdForClient(jwt);
    if (!actorClientId.equals(clientId)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Sem permissão para acessar este dependente.");
    }
  }

  private boolean isAdmin(Jwt jwt) {
    Object roles = jwt.getClaim("roles");
    if (roles instanceof Collection<?> values) {
      return values.stream().anyMatch(role -> "ROLE_ADMIN".equals(role.toString()));
    }
    return false;
  }

  private ResponseStatusException notFound() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Dependente não encontrado.");
  }
}
