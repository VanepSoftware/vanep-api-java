package br.com.vanep.drivercnh.service;

import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.drivercnh.dto.DriverCnhRequestDTO;
import br.com.vanep.drivercnh.dto.DriverCnhResponseDTO;
import br.com.vanep.drivercnh.mapper.DriverCnhMapper;
import br.com.vanep.drivercnh.model.DriverCnhModel;
import br.com.vanep.drivercnh.repository.DriverCnhRepository;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import java.util.List;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DriverCnhService {

  private final DriverCnhRepository cnhRepository;
  private final DriverRepository driverRepository;
  private final UserRepository userRepository;
  private final DriverCnhMapper mapper;
  private final MessageSource messages;

  public DriverCnhService(
      DriverCnhRepository cnhRepository,
      DriverRepository driverRepository,
      UserRepository userRepository,
      DriverCnhMapper mapper,
      MessageSource messages) {
    this.cnhRepository = cnhRepository;
    this.driverRepository = driverRepository;
    this.userRepository = userRepository;
    this.mapper = mapper;
    this.messages = messages;
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }

  @Transactional
  public DriverCnhResponseDTO create(DriverCnhRequestDTO request, String callerEmail) {
    UserModel caller =
        userRepository
            .findByEmail(callerEmail)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, message("user.account.not_found")));

    DriverModel driver;
    if (caller.getType() == UserType.ADMIN
        && request.driverToken() != null
        && !request.driverToken().isBlank()) {
      driver =
          driverRepository
              .findByToken(request.driverToken())
              .orElseThrow(
                  () ->
                      new ResponseStatusException(
                          HttpStatus.NOT_FOUND, message("driver_cnh.driver.not_found")));
    } else {
      driver =
          driverRepository
              .findByUserId(caller.getId())
              .orElseThrow(
                  () ->
                      new ResponseStatusException(
                          HttpStatus.NOT_FOUND, message("user.driver_profile.not_found")));
    }

    if (cnhRepository.existsByRegistrationNumber(request.registrationNumber())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, message("driver_cnh.registration.duplicate"));
    }

    if (cnhRepository.existsByDriverId(driver.getId())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, message("driver_cnh.driver.duplicate"));
    }

    DriverCnhModel cnh = new DriverCnhModel();
    cnh.setDriver(driver);
    applyRequest(cnh, request);

    return mapper.toResponse(cnhRepository.save(cnh));
  }

  public List<DriverCnhResponseDTO> findAll(String callerEmail) {
    UserModel caller =
        userRepository
            .findByEmail(callerEmail)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, message("user.account.not_found")));

    List<DriverCnhModel> cnhs;
    if (caller.getType() == UserType.ADMIN) {
      cnhs = cnhRepository.findAll();
    } else {
      DriverModel driver =
          driverRepository
              .findByUserId(caller.getId())
              .orElseThrow(
                  () ->
                      new ResponseStatusException(
                          HttpStatus.NOT_FOUND, message("user.driver_profile.not_found")));
      cnhs = cnhRepository.findByDriverId(driver.getId());
    }
    return cnhs.stream().map(mapper::toResponse).toList();
  }

  public DriverCnhResponseDTO findByToken(String token) {
    return mapper.toResponse(requireByToken(token));
  }

  @Transactional
  public DriverCnhResponseDTO update(String token, DriverCnhRequestDTO request) {
    DriverCnhModel cnh = requireByToken(token);

    if (!cnh.getRegistrationNumber().equals(request.registrationNumber())
        && cnhRepository.existsByRegistrationNumber(request.registrationNumber())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, message("driver_cnh.registration.duplicate"));
    }

    applyRequest(cnh, request);

    return mapper.toResponse(cnhRepository.save(cnh));
  }

  @Transactional
  public void delete(String token) {
    cnhRepository.delete(requireByToken(token));
  }

  @Transactional
  public DriverCnhResponseDTO restore(String token) {
    if (cnhRepository.existsDeletedByToken(token)) {
      cnhRepository.restoreByToken(token);
      return mapper.toResponse(requireByToken(token));
    }

    if (cnhRepository.findByToken(token).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, message("driver_cnh.already_active"));
    }

    throw new ResponseStatusException(HttpStatus.NOT_FOUND, message("driver_cnh.not_found"));
  }

  private void applyRequest(DriverCnhModel cnh, DriverCnhRequestDTO request) {
    cnh.setRegistrationNumber(request.registrationNumber());
    cnh.setCategory(request.category());
    cnh.setIssueDate(request.issueDate());
    cnh.setValidUntil(request.validUntil());
    cnh.setFirstLicenseDate(request.firstLicenseDate());
    cnh.setSecurityNumber(request.securityNumber());
    cnh.setIssuingState(request.issuingState());
    cnh.setPhotoUrl(request.photoUrl());
  }

  private DriverCnhModel requireByToken(String token) {
    return cnhRepository
        .findByToken(token)
        .orElseThrow(
            () ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, message("driver_cnh.not_found")));
  }
}
