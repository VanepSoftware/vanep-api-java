package br.com.vanep.dependent.service;

import br.com.vanep.address.dto.AddressResponseDTO;
import br.com.vanep.address.dto.DependentAddressRequestDTO;
import br.com.vanep.address.service.AddressService;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.dependent.dto.DependentCreateDTO;
import br.com.vanep.dependent.dto.DependentResponseDTO;
import br.com.vanep.dependent.dto.DependentSchoolDTO;
import br.com.vanep.dependent.dto.DependentSchoolDayRequestDTO;
import br.com.vanep.dependent.dto.DependentSchoolDayResponseDTO;
import br.com.vanep.dependent.dto.DependentSchoolDaysRequestDTO;
import br.com.vanep.dependent.dto.DependentUpdateDTO;
import br.com.vanep.dependent.mapper.DependentMapper;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.dependent.model.DependentSchoolDayModel;
import br.com.vanep.dependent.repository.DependentRepository;
import br.com.vanep.dependent.repository.DependentSchoolDayRepository;
import br.com.vanep.school.model.SchoolModel;
import br.com.vanep.school.repository.SchoolRepository;
import br.com.vanep.shared.validation.CpfValidator;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
  private final DependentSchoolDayRepository schoolDays;
  private final ClientRepository clients;
  private final SchoolRepository schools;
  private final UserRepository users;
  private final DependentMapper mapper;
  private final AddressService addressService;
  private final MessageSource messages;

  public DependentService(
      DependentRepository dependents,
      DependentSchoolDayRepository schoolDays,
      ClientRepository clients,
      SchoolRepository schools,
      UserRepository users,
      DependentMapper mapper,
      AddressService addressService,
      MessageSource messages) {
    this.dependents = dependents;
    this.schoolDays = schoolDays;
    this.clients = clients;
    this.schools = schools;
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
    String document = normalizeDocument(dto.getDocument());
    assertDocumentAvailable(document, null);

    DependentModel model = mapper.toModel(dto, clientId);
    model.setDocument(document);
    applySchoolOnCreate(dto.getSchoolToken(), model);
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
    // Hibernate drops pending updates of an entity it is about to delete, so the cleared
    // address_id is written first; otherwise the restored dependent points to a removed address.
    dependents.flush();
    dependents.delete(model);
    dependents.flush();
    promoteDefaultAfterDelete(clientId, wasDefault);
  }

  @Transactional
  public DependentResponseDTO restore(Jwt jwt, String token) {
    if (dependents.findByToken(token).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Dependent is already active.");
    }

    Long clientId = dependents.findClientIdOfDeletedByToken(token).orElseThrow(this::notFound);
    assertOwnership(jwt, clientId);

    dependents.clearActiveDefaultsIfDeletedIsDefault(clientId, token);
    dependents.restoreByToken(token);
    DependentModel restored = dependents.findByToken(token).orElseThrow(this::notFound);
    return toResponse(restored);
  }

  @Transactional(readOnly = true)
  public List<DependentSchoolDayResponseDTO> findSchoolDays(Jwt jwt, String token) {
    DependentModel dependent = findActiveForAccess(jwt, token);
    return mapper.toSchoolDayResponses(schoolDays.findByDependentId(dependent.getId()));
  }

  @Transactional
  public List<DependentSchoolDayResponseDTO> replaceSchoolDays(
      Jwt jwt, String token, DependentSchoolDaysRequestDTO request) {
    DependentModel dependent = lockActiveForAccess(jwt, token);
    ensureDistinctWeekdays(request.days());

    schoolDays.deleteAll(schoolDays.findByDependentId(dependent.getId()));
    // Soft deletes run after inserts on flush; flushing first frees the weekdays held by the
    // partial unique index.
    schoolDays.flush();
    List<DependentSchoolDayModel> replaced =
        schoolDays.saveAll(
            request.days().stream()
                .map(day -> mapper.toSchoolDayModel(dependent.getId(), day))
                .toList());
    return mapper.toSchoolDayResponses(replaced);
  }

  private void ensureDistinctWeekdays(List<DependentSchoolDayRequestDTO> days) {
    long distinctWeekdays = days.stream().map(day -> day.weekday()).distinct().count();
    if (distinctWeekdays < days.size()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, message("dependent.school_days.weekday_duplicate"));
    }
  }

  private void applyScalarMerge(DependentUpdateDTO dto, DependentModel model) {
    applyName(dto.name(), model);
    applyNullable(dto.birthDate(), model::setBirthDate);
    applyNullable(dto.gender(), model::setGender);
    applyDocument(dto.document(), model);
    applyNullable(dto.phone(), model::setPhone);
    applyNullable(dto.email(), model::setEmail);
    applyIsSelf(dto.isSelf(), model);
    applyDefaultFlag(dto.isDefault(), model);
    applySchoolTokenMerge(dto.schoolToken(), model);
  }

  private void applyAddressMerge(Long dependentId, DependentAddressRequestDTO address) {
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
    String document = normalizeDocument(documentField.get());
    if (document != null && !Objects.equals(document, model.getDocument())) {
      assertDocumentAvailable(document, model.getToken());
    }
    model.setDocument(document);
  }

  private String normalizeDocument(String document) {
    return StringUtils.hasText(document) ? CpfValidator.normalize(document) : null;
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

  private void applySchoolOnCreate(String schoolToken, DependentModel model) {
    if (schoolToken == null) {
      return;
    }
    model.setSchoolId(requireSchoolId(schoolToken));
  }

  private void applySchoolTokenMerge(JsonNullable<String> schoolToken, DependentModel model) {
    if (!schoolToken.isPresent()) {
      return;
    }
    String token = schoolToken.get();
    if (token == null) {
      model.setSchoolId(null);
      return;
    }
    model.setSchoolId(requireSchoolId(token));
  }

  private Long requireSchoolId(String schoolToken) {
    if (!StringUtils.hasText(schoolToken)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, message("dependent.school_token.blank"));
    }
    return schools
        .findByToken(schoolToken)
        .map(school -> school.getId())
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, message("school.not_found")));
  }

  private DependentModel findActiveForAccess(Jwt jwt, String token) {
    DependentModel model = dependents.findByToken(token).orElseThrow(this::notFound);
    assertOwnership(jwt, model.getClientId());
    return model;
  }

  // Two replacements of the same grid would otherwise insert the same weekdays at once and break
  // the partial unique index; the lock makes the later one wait and replace the earlier one.
  private DependentModel lockActiveForAccess(Jwt jwt, String token) {
    DependentModel model = dependents.lockByToken(token).orElseThrow(this::notFound);
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
        toSchoolDto(model.getSchoolId()),
        addressService.toResponseOrNull(model.getAddressId()));
  }

  private List<DependentResponseDTO> toResponses(List<DependentModel> models) {
    Map<Long, String> clientTokens = clientTokensById(models);
    Map<Long, SchoolModel> schoolsById = schoolsById(models);
    Map<Long, AddressResponseDTO> addresses =
        addressService.toResponsesByIds(addressIdsOf(models, schoolsById.values()));
    return models.stream()
        .map(
            model ->
                mapper.toResponse(
                    model,
                    clientTokens.get(model.getClientId()),
                    schoolDtoFor(model, schoolsById, addresses),
                    addressOf(model.getAddressId(), addresses)))
        .toList();
  }

  static List<Long> addressIdsOf(List<DependentModel> models, Collection<SchoolModel> schools) {
    return Stream.concat(
            models.stream().map(dependent -> dependent.getAddressId()),
            schools.stream().map(school -> school.getAddressId()))
        .filter(Objects::nonNull)
        .distinct()
        .toList();
  }

  static AddressResponseDTO addressOf(Long addressId, Map<Long, AddressResponseDTO> addresses) {
    return addressId == null ? null : addresses.get(addressId);
  }

  private DependentSchoolDTO schoolDtoFor(
      DependentModel model,
      Map<Long, SchoolModel> schoolsById,
      Map<Long, AddressResponseDTO> addresses) {
    Long schoolId = model.getSchoolId();
    if (schoolId == null) {
      return null;
    }
    SchoolModel school = schoolsById.get(schoolId);
    return school == null ? null : toSchoolDto(school, addressOf(school.getAddressId(), addresses));
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

  private Map<Long, SchoolModel> schoolsById(List<DependentModel> models) {
    List<Long> schoolIds =
        models.stream()
            .map(dependent -> dependent.getSchoolId())
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    if (schoolIds.isEmpty()) {
      return Map.of();
    }
    return schools.findAllById(schoolIds).stream()
        .collect(Collectors.toMap(school -> school.getId(), school -> school));
  }

  private DependentSchoolDTO toSchoolDto(Long schoolId) {
    if (schoolId == null) {
      return null;
    }
    return schools
        .findById(schoolId)
        .map(school -> toSchoolDto(school, addressService.toResponseOrNull(school.getAddressId())))
        .orElse(null);
  }

  private DependentSchoolDTO toSchoolDto(SchoolModel school, AddressResponseDTO address) {
    return new DependentSchoolDTO(school.getToken(), school.getName(), address);
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
              dependents.saveAndFlush(dependent);
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
    ResponseStatusException exception =
        new ResponseStatusException(HttpStatus.NOT_FOUND, message("dependent.not_found"));
    exception.getBody().setProperty("code", "dependent.not_found");
    return exception;
  }
}
