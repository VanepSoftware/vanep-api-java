package br.com.vanep.driver.service;

import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.dto.DriverAvailabilityRequestDTO;
import br.com.vanep.driver.dto.DriverMeSummaryResponseDTO;
import br.com.vanep.driver.dto.DriverResponseDTO;
import br.com.vanep.driver.dto.DriverUpdateRequestDTO;
import br.com.vanep.driver.mapper.DriverMapper;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.driverrating.repository.DriverRatingRepository;
import br.com.vanep.shared.error.ApiProblemException;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DriverService {
  private final DriverRepository driverRepository;
  private final DriverMapper mapper;
  private final UserService userService;
  private final DriverRatingRepository ratings;

  public DriverService(
      DriverRepository driverRepository,
      DriverMapper mapper,
      UserService userService,
      DriverRatingRepository ratings) {
    this.driverRepository = driverRepository;
    this.mapper = mapper;
    this.userService = userService;
    this.ratings = ratings;
  }

  @Transactional(readOnly = true)
  public Page<DriverResponseDTO> findAll(Pageable pageable) {
    return driverRepository.findAll(pageable).map(mapper::toResponse);
  }

  @Transactional(readOnly = true)
  public DriverResponseDTO findByToken(String token) {
    return mapper.toResponse(requireByToken(token));
  }

  @Transactional(readOnly = true)
  public DriverMeSummaryResponseDTO getMyProfile(String uid) {
    UserModel user = userService.requireByTokenAndType(uid, UserType.DRIVER);
    DriverModel driver = requireByUserId(user.getId());
    return toMeSummary(driver, user);
  }

  @Transactional
  public DriverMeSummaryResponseDTO updateMyAvailability(
      String uid, DriverAvailabilityRequestDTO request) {
    UserModel user = userService.requireByTokenAndType(uid, UserType.DRIVER);
    DriverModel driver = requireByUserId(user.getId());
    driver.setAvailable(request.available());
    return toMeSummary(driverRepository.save(driver), user);
  }

  DriverMeSummaryResponseDTO toMeSummary(DriverModel driver, UserModel user) {
    return mapper.toMeSummary(
        driver, ratings.countByDriverId(driver.getId()), userService.toMeResponse(user));
  }

  public DriverModel requireCallerDriver(String uid) {
    return requireByUserId(userService.requireByTokenAndType(uid, UserType.DRIVER).getId());
  }

  @Transactional
  public DriverResponseDTO update(String token, DriverUpdateRequestDTO request) {
    DriverModel driver = requireByToken(token);
    applyUpdate(driver, request);
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
      throw ApiProblemException.conflict("driver.already_active");
    }

    throw ApiProblemException.notFound("driver.not_found");
  }

  private void applyUpdate(DriverModel driver, DriverUpdateRequestDTO request) {
    driver.setBio(request.bio());
    driver.setCnpj(request.cnpj());
    driver.setExperienceYears(request.experienceYears());
    driver.setBasePrice(request.basePrice());
    driver.setWorkStartTime(request.workStartTime());
    driver.setWorkEndTime(request.workEndTime());
    driver.setWorkDays(request.workDays());
    driver.setWaitToleranceMinutes(request.waitToleranceMinutes());
    driver.setAvailable(request.available());
  }

  private DriverModel requireByUserId(Long userId) {
    return driverRepository
        .findByUserId(userId)
        .orElseThrow(() -> ApiProblemException.notFound("user.driver_profile.not_found"));
  }

  private DriverModel requireByToken(String token) {
    return driverRepository
        .findByToken(token)
        .orElseThrow(() -> ApiProblemException.notFound("driver.not_found"));
  }
}
