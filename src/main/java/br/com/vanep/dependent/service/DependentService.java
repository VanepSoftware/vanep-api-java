package br.com.vanep.dependent.service;

import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.dependent.dto.DependentCreateDTO;
import br.com.vanep.dependent.dto.DependentResponseDTO;
import br.com.vanep.dependent.dto.DependentUpdateDTO;
import br.com.vanep.dependent.mapper.DependentMapper;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.dependent.repository.DependentRepository;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.model.UserModel;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
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

    DependentModel model =
        mapper.toModel(
            dto,
            clientId,
            resolveSchoolId(dto.getSchoolToken()).orElse(null),
            resolveAddressId(dto.getAddressToken()).orElse(null));
    applyDefaultOnCreate(model, dto, clientId);

    DependentModel saved = dependents.save(model);
    return toResponse(saved);
  }

  @Transactional(readOnly = true)
  public List<DependentResponseDTO> list(Jwt jwt) {
    if (isAdmin(jwt)) {
      return toResponses(dependents.findAll());
    }
    Long clientId = resolveClientIdForClient(jwt);
    return toResponses(dependents.findByClientId(clientId));
  }

  @Transactional(readOnly = true)
  public DependentResponseDTO getByToken(Jwt jwt, String token) {
    DependentModel model = findActiveForAccess(jwt, token);
    return toResponse(model);
  }

  @Transactional
  public DependentResponseDTO update(Jwt jwt, String token, DependentUpdateDTO dto) {
    DependentModel model = findActiveForAccess(jwt, token);
    if (dto.getDocument() != null) {
      assertDocumentAvailable(dto.getDocument(), token);
    }

    mapper.applyUpdate(dto, model);
    applySchoolTokenUpdate(dto.getSchoolToken(), model);
    applyAddressTokenUpdate(dto.getAddressToken(), model);
    if (Boolean.TRUE.equals(dto.getIsDefault())) {
      clearOtherDefaults(model.getClientId(), model.getToken());
      model.setDefaultDependent(true);
    } else if (Boolean.FALSE.equals(dto.getIsDefault())) {
      model.setDefaultDependent(false);
    }

    DependentModel saved = dependents.save(model);
    return toResponse(saved);
  }

  @Transactional
  public void delete(Jwt jwt, String token) {
    DependentModel model = findActiveForAccess(jwt, token);
    boolean wasDefault = model.isDefaultDependent();
    Long clientId = model.getClientId();

    dependents.delete(model);
    promoteDefaultAfterDelete(clientId, wasDefault);
  }

  @Transactional
  public DependentResponseDTO restore(Jwt jwt, String token) {
    if (dependents.findByToken(token).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Dependent is already active.");
    }

    Long clientId = dependents.findClientIdOfDeletedByToken(token).orElseThrow(this::notFound);
    assertOwnership(jwt, clientId);

    dependents.restoreByToken(token);
    DependentModel restored = dependents.findByToken(token).orElseThrow(this::notFound);
    return toResponse(restored);
  }

  private DependentModel findActiveForAccess(Jwt jwt, String token) {
    DependentModel model = dependents.findByToken(token).orElseThrow(this::notFound);
    assertOwnership(jwt, model.getClientId());
    return model;
  }

  private DependentResponseDTO toResponse(DependentModel model) {
    String clientToken = resolveClientToken(model.getClientId());
    String schoolToken = resolveSchoolToken(model.getSchoolId()).orElse(null);
    String addressToken = resolveAddressToken(model.getAddressId()).orElse(null);
    return mapper.toResponse(model, clientToken, schoolToken, addressToken);
  }

  private List<DependentResponseDTO> toResponses(List<DependentModel> models) {
    Map<Long, String> clientTokens = clientTokensById(models);
    return models.stream()
        .map(
            model ->
                mapper.toResponse(
                    model,
                    clientTokens.get(model.getClientId()),
                    resolveSchoolToken(model.getSchoolId()).orElse(null),
                    resolveAddressToken(model.getAddressId()).orElse(null)))
        .toList();
  }

  private Map<Long, String> clientTokensById(List<DependentModel> models) {
    List<Long> clientIds =
        models.stream().map(model -> model.getClientId()).distinct().toList();
    return clients.findAllById(clientIds).stream()
        .collect(Collectors.toMap(client -> client.getId(), client -> client.getToken()));
  }

  private String resolveClientToken(Long clientId) {
    return clients
        .findById(clientId)
        .map(client -> client.getToken())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found."));
  }

  private Optional<Long> resolveSchoolId(String schoolToken) {
    if (schoolToken == null || !StringUtils.hasText(schoolToken)) {
      return Optional.empty();
    }
    throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST, "schoolToken is not supported in this phase.");
  }

  private Optional<Long> resolveAddressId(String addressToken) {
    if (addressToken == null || !StringUtils.hasText(addressToken)) {
      return Optional.empty();
    }
    throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST, "addressToken is not supported in this phase.");
  }

  private Optional<String> resolveSchoolToken(Long schoolId) {
    return Optional.empty();
  }

  private Optional<String> resolveAddressToken(Long addressId) {
    return Optional.empty();
  }

  private void applySchoolTokenUpdate(String schoolToken, DependentModel model) {
    if (schoolToken == null) {
      return;
    }
    if (!StringUtils.hasText(schoolToken)) {
      model.setSchoolId(null);
      return;
    }
    model.setSchoolId(resolveSchoolId(schoolToken).orElse(null));
  }

  private void applyAddressTokenUpdate(String addressToken, DependentModel model) {
    if (addressToken == null) {
      return;
    }
    if (!StringUtils.hasText(addressToken)) {
      model.setAddressId(null);
      return;
    }
    model.setAddressId(resolveAddressId(addressToken).orElse(null));
  }

  private void promoteDefaultAfterDelete(Long clientId, boolean wasDefault) {
    if (!wasDefault) {
      return;
    }
    List<DependentModel> remaining = dependents.findByClientId(clientId);
    if (remaining.size() == 1) {
      DependentModel only = remaining.getFirst();
      only.setDefaultDependent(true);
      dependents.save(only);
    }
  }

  private void applyDefaultOnCreate(DependentModel model, DependentCreateDTO dto, Long clientId) {
    long activeCount = dependents.countByClientId(clientId);
    if (activeCount == 0) {
      model.setDefaultDependent(true);
      return;
    }
    if (Boolean.TRUE.equals(dto.getIsDefault())) {
      clearOtherDefaults(clientId, null);
      model.setDefaultDependent(true);
    } else {
      model.setDefaultDependent(false);
    }
  }

  private void clearOtherDefaults(Long clientId, String excludeToken) {
    dependents.findByClientId(clientId).stream()
        .filter(dependent -> excludeToken == null || !dependent.getToken().equals(excludeToken))
        .filter(dependent -> dependent.isDefaultDependent())
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
          HttpStatus.CONFLICT, "Document is already registered for another dependent.");
    }
  }

  private Long resolveClientIdForCreate(Jwt jwt, DependentCreateDTO dto) {
    if (isAdmin(jwt)) {
      if (!StringUtils.hasText(dto.getClientToken())) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "clientToken is required for administrators.");
      }
      return clients
          .findByToken(dto.getClientToken())
          .map(client -> client.getId())
          .orElseThrow(
              () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found."));
    }
    return resolveClientIdForClient(jwt);
  }

  private Long resolveClientIdForClient(Jwt jwt) {
    UserModel user = requireUser(jwt);
    return clients
        .findByUserId(user.getId())
        .map(client -> client.getId())
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Client profile not found."));
  }

  private UserModel requireUser(Jwt jwt) {
    return users
        .findByEmail(jwt.getSubject())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found."));
  }

  private void assertOwnership(Jwt jwt, Long clientId) {
    if (isAdmin(jwt)) {
      return;
    }
    Long actorClientId = resolveClientIdForClient(jwt);
    if (!actorClientId.equals(clientId)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Not allowed to access this dependent.");
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
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Dependent not found.");
  }
}
