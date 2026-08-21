package br.com.vanep.school.service;

import br.com.vanep.address.dto.AddressRequestDTO;
import br.com.vanep.address.service.AddressService;
import br.com.vanep.school.dto.SchoolRequestDTO;
import br.com.vanep.school.dto.SchoolResponseDTO;
import br.com.vanep.school.dto.SchoolUpdateRequestDTO;
import br.com.vanep.school.mapper.SchoolMapper;
import br.com.vanep.school.model.SchoolModel;
import br.com.vanep.school.repository.SchoolRepository;
import java.util.Objects;
import java.util.function.Consumer;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SchoolService {

  private final SchoolRepository schoolRepository;
  private final SchoolMapper mapper;
  private final AddressService addressService;
  private final MessageSource messages;

  public SchoolService(
      SchoolRepository schoolRepository,
      SchoolMapper mapper,
      AddressService addressService,
      MessageSource messages) {
    this.schoolRepository = schoolRepository;
    this.mapper = mapper;
    this.addressService = addressService;
    this.messages = messages;
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }

  public Page<SchoolResponseDTO> findAll(Pageable pageable) {
    Page<SchoolModel> page = schoolRepository.findAll(pageable);
    var addressesById =
        addressService.toResponsesByIds(
            page.getContent().stream()
                .map(school -> school.getAddressId())
                .filter(Objects::nonNull)
                .distinct()
                .toList());
    return page.map(
        school ->
            mapper.toResponse(
                school,
                school.getAddressId() == null ? null : addressesById.get(school.getAddressId())));
  }

  public SchoolResponseDTO findByToken(String token) {
    return toResponse(requireByToken(token));
  }

  @Transactional
  public SchoolResponseDTO create(SchoolRequestDTO request) {
    if (request.cnpj() != null && schoolRepository.existsByCnpj(request.cnpj())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, message("school.cnpj.duplicate"));
    }

    SchoolModel school = new SchoolModel();
    applyRequest(school, request);
    SchoolModel saved = schoolRepository.save(school);
    if (request.address() != null) {
      addressService.upsertForSchool(saved.getId(), request.address());
      saved = requireById(saved.getId());
    }
    return toResponse(saved);
  }

  @Transactional
  public SchoolResponseDTO update(String token, SchoolUpdateRequestDTO request) {
    SchoolModel school = requireByToken(token);
    applyScalarMerge(request, school);
    SchoolModel saved = schoolRepository.save(school);
    if (request.address().isPresent()) {
      applyAddressMerge(saved.getId(), request.address().get());
      saved = requireById(saved.getId());
    }
    return toResponse(saved);
  }

  @Transactional
  public void delete(String token) {
    SchoolModel school = requireByToken(token);
    addressService.clearForSchool(school.getId());
    schoolRepository.delete(school);
  }

  @Transactional
  public SchoolResponseDTO restore(String token) {
    if (schoolRepository.existsDeletedByToken(token)) {
      schoolRepository.restoreByToken(token);
      return toResponse(requireByToken(token));
    }

    if (schoolRepository.findByToken(token).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, message("school.already_active"));
    }

    throw new ResponseStatusException(HttpStatus.NOT_FOUND, message("school.not_found"));
  }

  private void applyRequest(SchoolModel school, SchoolRequestDTO request) {
    school.setName(request.name());
    school.setCnpj(request.cnpj());
    school.setPhone(request.phone());
    school.setEmail(request.email());
  }

  private void applyScalarMerge(SchoolUpdateRequestDTO request, SchoolModel school) {
    applyName(request.name(), school);
    applyCnpj(request.cnpj(), school);
    applyNullable(request.phone(), school::setPhone);
    applyNullable(request.email(), school::setEmail);
  }

  private void applyAddressMerge(Long schoolId, AddressRequestDTO address) {
    if (address == null) {
      addressService.clearForSchool(schoolId);
      return;
    }
    addressService.upsertForSchool(schoolId, address);
  }

  private void applyName(JsonNullable<String> nameField, SchoolModel school) {
    if (!nameField.isPresent()) {
      return;
    }
    String name = nameField.get();
    if (name == null || name.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message("school.name.blank"));
    }
    school.setName(name);
  }

  private void applyCnpj(JsonNullable<String> cnpjField, SchoolModel school) {
    if (!cnpjField.isPresent()) {
      return;
    }
    String cnpj = cnpjField.get();
    if (cnpj != null && !Objects.equals(cnpj, school.getCnpj())) {
      if (schoolRepository.existsByCnpjAndTokenNot(cnpj, school.getToken())) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, message("school.cnpj.duplicate"));
      }
    }
    school.setCnpj(cnpj);
  }

  private <T> void applyNullable(JsonNullable<T> field, Consumer<T> setter) {
    if (field.isPresent()) {
      setter.accept(field.get());
    }
  }

  private SchoolResponseDTO toResponse(SchoolModel school) {
    return mapper.toResponse(school, addressService.toResponseOrNull(school.getAddressId()));
  }

  private SchoolModel requireByToken(String token) {
    return schoolRepository
        .findByToken(token)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, message("school.not_found")));
  }

  private SchoolModel requireById(Long id) {
    return schoolRepository
        .findById(id)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, message("school.not_found")));
  }
}
