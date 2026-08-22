package br.com.vanep.dependent.service;

import br.com.vanep.address.dto.AddressRequestDTO;
import br.com.vanep.address.dto.AddressResponseDTO;
import br.com.vanep.address.service.AddressService;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.dependent.dto.DependentCreateDTO;
import br.com.vanep.dependent.dto.DependentResponseDTO;
import br.com.vanep.dependent.dto.DependentUpdateDTO;
import br.com.vanep.dependent.enums.Shift;
import br.com.vanep.dependent.mapper.DependentMapper;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.dependent.repository.DependentRepository;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
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
  private final AddressService addressService;
  private final MessageSource messages;

  public DependentService(
      DependentRepository dependents,
      ClientRepository clients,
      UserRepository users,
      DependentMapper mapper,
      AddressService addressService,
      MessageSource messages) {
    this.dependents = dependents;
    this.clients = clients;
    this.users = users;
    this.mapper = mapper;
    this.addressService = addressService;
    this.messages = messages;
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }

  @Transactional
  public DependentResponseDTO create(Jwt jwt, DependentCreateDTO dto) {
    Long clientId = resolveClientIdForCreate(jwt, dto);
    rejectPresentSchoolToken(dto.getSchoolToken());
    assertDocumentAvailable(dto.getDocument(), null);

    DependentModel model = mapper.toModel(dto, clientId);
    applyDefaultOnCreate(model, dto, clientId);

    DependentModel saved = dependents.save(model);
    if (dto.getAddress() != null) {
      addressService.upsertForDependent(saved.getId(), dto.getAddress());
      saved = requireById(saved.getId());
    }
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
    rejectPresentSchoolToken(dto.schoolToken());
    applyScalarMerge(dto, model);

    DependentModel saved = dependents.save(model);
    if (dto.address().isPresent()) {
      applyAddressMerge(saved.getId(), dto.address().get());
      saved = requireById(saved.getId());
    }
    return toResponse(saved);
  }

  @Transactional
  public void delete(Jwt jwt, String token) {
    DependentModel model = findActiveForAccess(jwt, token);
    boolean wasDefault = model.isDefaultDependent();
    Long clientId = model.getClientId();

    addressService.clearForDependent(model.getId());
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

  private void applyScalarMerge(DependentUpdateDTO dto, DependentModel model) {
    applyName(dto.name(), model);
    applyNullable(dto.birthDate(), model::setBirthDate);
    applyNullable(dto.gender(), model::setGender);
    applyDocument(dto.document(), model);
    applyNullable(dto.phone(), model::setPhone);
    applyNullable(dto.email(), model::setEmail);
    applyIsSelf(dto.isSelf(), model);
    applyRequiredShift(dto.shift(), model);
    applyDefaultFlag(dto.isDefault(), model);
  }

  private void applyAddressMerge(Long dependentId, AddressRequestDTO address) {
    if (address == null) {
      addressService.clearForDependent(dependentId);
      return;
    }
    addressService.upsertForDependent(dependentId, address);
  }

  private void applyName(JsonNullable<String> nameField, DependentModel model) {
    if (!nameField.isPresent()) {
      return;
    }
    String name = nameField.get();
    if (name == null || name.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message("dependent.name.blank"));
    }
    model.setName(name);
  }

  private <T> void applyNullable(JsonNullable<T> field, Consumer<T> setter) {
    if (field.isPresent()) {
      setter.accept(field.get());
    }
  }

  private void applyDocument(JsonNullable<String> documentField, DependentModel model) {
    if (!documentField.isPresent()) {
      return;
    }
    String document = documentField.get();
    if (document != null && !Objects.equals(document, model.getDocument())) {
      assertDocumentAvailable(document, model.getToken());
    }
    model.setDocument(document);
  }

  private void applyIsSelf(JsonNullable<Boolean> field, DependentModel model) {
    if (!field.isPresent()) {
      return;
    }
    Boolean value = field.get();
    if (value == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message("dependent.field.null"));
    }
    model.setSelf(value);
  }

  private void applyRequiredShift(JsonNullable<Shift> shiftField, DependentModel model) {
    if (!shiftField.isPresent()) {
      return;
    }
    Shift shift = shiftField.get();
    if (shift == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message("dependent.field.null"));
    }
    model.setShift(shift);
  }

  private void applyDefaultFlag(JsonNullable<Boolean> isDefaultField, DependentModel model) {
    if (!isDefaultField.isPresent()) {
      return;
    }
    Boolean isDefault = isDefaultField.get();
    if (isDefault == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message("dependent.field.null"));
    }
    if (isDefault) {
      clearOtherDefaults(model.getClientId(), model.getToken());
      model.setDefaultDependent(true);
    } else {
      model.setDefaultDependent(false);
    }
  }

  // TODO(follow-up): school already exists — replace these rejects with schoolToken
  // merge (resolve by token, 404 school.not_found; PATCH null clears).
  private void rejectPresentSchoolToken(String schoolToken) {
    if (schoolToken != null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, message("dependent.school_token.unsupported"));
    }
  }

  private void rejectPresentSchoolToken(JsonNullable<String> schoolToken) {
    if (schoolToken.isPresent()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, message("dependent.school_token.unsupported"));
    }
  }

  private DependentModel findActiveForAccess(Jwt jwt, String token) {
    DependentModel model = dependents.findByToken(token).orElseThrow(this::notFound);
    assertOwnership(jwt, model.getClientId());
    return model;
  }

  private DependentModel requireById(Long id) {
    return dependents.findById(id).orElseThrow(this::notFound);
  }

  private DependentResponseDTO toResponse(DependentModel model) {
    return mapper.toResponse(
        model,
        resolveClientToken(model.getClientId()),
        resolveSchoolToken(model.getSchoolId()).orElse(null),
        addressService.toResponseOrNull(model.getAddressId()));
  }

  private List<DependentResponseDTO> toResponses(List<DependentModel> models) {
    Map<Long, String> clientTokens = clientTokensById(models);
    Map<Long, AddressResponseDTO> addresses =
        addressService.toResponsesByIds(
            models.stream()
                .map(dependent -> dependent.getAddressId())
                .filter(Objects::nonNull)
                .distinct()
                .toList());
    return models.stream()
        .map(
            model ->
                mapper.toResponse(
                    model,
                    clientTokens.get(model.getClientId()),
                    resolveSchoolToken(model.getSchoolId()).orElse(null),
                    model.getAddressId() == null ? null : addresses.get(model.getAddressId())))
        .toList();
  }

  private Map<Long, String> clientTokensById(List<DependentModel> models) {
    List<Long> clientIds = models.stream().map(model -> model.getClientId()).distinct().toList();
    return clients.findAllById(clientIds).stream()
        .collect(Collectors.toMap(client -> client.getId(), client -> client.getToken()));
  }

  private String resolveClientToken(Long clientId) {
    return clients
        .findById(clientId)
        .map(client -> client.getToken())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found."));
  }

  private Optional<String> resolveSchoolToken(Long schoolId) {
    return Optional.empty();
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
          HttpStatus.CONFLICT, message("dependent.document.duplicate"));
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
    return new ResponseStatusException(HttpStatus.NOT_FOUND, message("dependent.not_found"));
  }
}
