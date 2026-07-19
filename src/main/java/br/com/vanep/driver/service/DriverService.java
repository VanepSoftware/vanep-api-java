package br.com.vanep.driver.service;

import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.dto.DriverResponseDTO;
import br.com.vanep.driver.dto.DriverUpdateRequestDTO;
import br.com.vanep.driver.mapper.DriverMapper;
import br.com.vanep.driver.model.DriverModel;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DriverService {

  private final DriverRepository driverRepository;
  private final DriverMapper mapper;
  private final MessageSource messages;

  public DriverService(
      DriverRepository driverRepository, DriverMapper mapper, MessageSource messages) {
    this.driverRepository = driverRepository;
    this.mapper = mapper;
    this.messages = messages;
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }

  @Transactional(readOnly = true)
  public Page<DriverResponseDTO> findAll(Pageable pageable) {
    return driverRepository.findAll(pageable).map(mapper::toResponse);
  }

  @Transactional(readOnly = true)
  public DriverResponseDTO findByToken(String token) {
    return mapper.toResponse(requireByToken(token));
  }

  @Transactional
  public DriverResponseDTO update(String token, DriverUpdateRequestDTO request) {
    DriverModel driver = requireByToken(token);

    driver.setPhoto(request.photo());
    driver.setBio(request.bio());
    driver.setCnpj(request.cnpj());
    driver.setExperienceYears(request.experienceYears());
    driver.setCity(request.city());
    driver.setBasePrice(request.basePrice());
    driver.setWorkStartTime(request.workStartTime());
    driver.setWorkEndTime(request.workEndTime());
    driver.setWorkDays(request.workDays());
    driver.setWaitToleranceMinutes(request.waitToleranceMinutes());
    driver.setServiceAreas(request.serviceAreas());
    driver.setAvailable(request.available());

    return mapper.toResponse(driverRepository.save(driver));
  }

  @Transactional
  public void delete(String token) {
    driverRepository.delete(requireByToken(token));
  }

  @Transactional
  public DriverResponseDTO restore(String token) {
    if (driverRepository.existsDeletedByToken(token)) {
      driverRepository.restoreByToken(token);
      DriverModel restored = requireByToken(token);
      return mapper.toResponse(restored);
    }

    if (driverRepository.findByToken(token).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, message("driver.already_active"));
    }

    throw new ResponseStatusException(HttpStatus.NOT_FOUND, message("driver.not_found"));
  }

  private DriverModel requireByToken(String token) {
    return driverRepository
        .findByToken(token)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, message("driver.not_found")));
  }
}
