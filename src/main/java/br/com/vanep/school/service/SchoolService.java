package br.com.vanep.school.service;

import br.com.vanep.school.dto.SchoolRequestDTO;
import br.com.vanep.school.dto.SchoolResponseDTO;
import br.com.vanep.school.mapper.SchoolMapper;
import br.com.vanep.school.model.SchoolModel;
import br.com.vanep.school.repository.SchoolRepository;
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
  private final MessageSource messages;

  public SchoolService(
      SchoolRepository schoolRepository, SchoolMapper mapper, MessageSource messages) {
    this.schoolRepository = schoolRepository;
    this.mapper = mapper;
    this.messages = messages;
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }

  public Page<SchoolResponseDTO> findAll(Pageable pageable) {
    return schoolRepository.findAll(pageable).map(mapper::toResponse);
  }

  public SchoolResponseDTO findByToken(String token) {
    return mapper.toResponse(requireByToken(token));
  }

  @Transactional
  public SchoolResponseDTO create(SchoolRequestDTO request) {
    if (request.cnpj() != null && schoolRepository.existsByCnpj(request.cnpj())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, message("school.cnpj.duplicate"));
    }

    SchoolModel school = new SchoolModel();
    applyRequest(school, request);
    return mapper.toResponse(schoolRepository.save(school));
  }

  @Transactional
  public SchoolResponseDTO update(String token, SchoolRequestDTO request) {
    SchoolModel school = requireByToken(token);

    if (request.cnpj() != null
        && !request.cnpj().equals(school.getCnpj())
        && schoolRepository.existsByCnpj(request.cnpj())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, message("school.cnpj.duplicate"));
    }

    applyRequest(school, request);
    return mapper.toResponse(schoolRepository.save(school));
  }

  @Transactional
  public void delete(String token) {
    schoolRepository.delete(requireByToken(token));
  }

  @Transactional
  public SchoolResponseDTO restore(String token) {
    if (schoolRepository.existsDeletedByToken(token)) {
      schoolRepository.restoreByToken(token);
      return mapper.toResponse(requireByToken(token));
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
    school.setAddressId(request.addressId());
  }

  private SchoolModel requireByToken(String token) {
    return schoolRepository
        .findByToken(token)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, message("school.not_found")));
  }
}
