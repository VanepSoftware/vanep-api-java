package br.com.vanep.vehicle.service;

import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.vehicle.dto.VehicleRequestDTO;
import br.com.vanep.vehicle.dto.VehicleResponseDTO;
import br.com.vanep.vehicle.mapper.VehicleMapper;
import br.com.vanep.vehicle.model.VehicleModel;
import br.com.vanep.vehicle.repository.VehicleRepository;
import java.util.List;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class VehicleService {

  private final VehicleRepository vehicleRepository;
  private final DriverRepository driverRepository;
  private final UserRepository userRepository;
  private final VehicleMapper mapper;
  private final MessageSource messages;

  public VehicleService(
      VehicleRepository vehicleRepository,
      DriverRepository driverRepository,
      UserRepository userRepository,
      VehicleMapper mapper,
      MessageSource messages) {
    this.vehicleRepository = vehicleRepository;
    this.driverRepository = driverRepository;
    this.userRepository = userRepository;
    this.mapper = mapper;
    this.messages = messages;
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }

  @Transactional
  public VehicleResponseDTO create(VehicleRequestDTO request, String callerEmail) {
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
                          HttpStatus.NOT_FOUND, message("vehicle.driver.not_found")));
    } else {
      driver =
          driverRepository
              .findByUserId(caller.getId())
              .orElseThrow(
                  () ->
                      new ResponseStatusException(
                          HttpStatus.NOT_FOUND, message("user.driver_profile.not_found")));
    }

    if (vehicleRepository.existsByPlate(request.plate())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, message("vehicle.plate.duplicate"));
    }

    VehicleModel vehicle = new VehicleModel();
    vehicle.setDriver(driver);
    vehicle.setPlate(request.plate());
    vehicle.setBrand(request.brand());
    vehicle.setModel(request.model());
    vehicle.setManufactureYear(request.manufactureYear());
    vehicle.setColor(request.color());
    vehicle.setCapacity(request.capacity());
    vehicle.setPhotoFrontUrl(request.photoFrontUrl());
    vehicle.setPhotoSideUrl(request.photoSideUrl());
    vehicle.setPhotoDocumentUrl(request.photoDocumentUrl());

    return mapper.toResponse(vehicleRepository.save(vehicle));
  }

  public List<VehicleResponseDTO> findAll(String callerEmail) {
    UserModel caller =
        userRepository
            .findByEmail(callerEmail)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, message("user.account.not_found")));

    List<VehicleModel> vehicles;
    if (caller.getType() == UserType.ADMIN) {
      vehicles = vehicleRepository.findAll();
    } else {
      DriverModel driver =
          driverRepository
              .findByUserId(caller.getId())
              .orElseThrow(
                  () ->
                      new ResponseStatusException(
                          HttpStatus.NOT_FOUND, message("user.driver_profile.not_found")));
      vehicles = vehicleRepository.findByDriverId(driver.getId());
    }
    return vehicles.stream().map(mapper::toResponse).toList();
  }

  public VehicleResponseDTO findByToken(String token) {
    return mapper.toResponse(requireByToken(token));
  }

  @Transactional
  public VehicleResponseDTO update(String token, VehicleRequestDTO request) {
    VehicleModel vehicle = requireByToken(token);

    if (!vehicle.getPlate().equalsIgnoreCase(request.plate())
        && vehicleRepository.existsByPlate(request.plate())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, message("vehicle.plate.duplicate"));
    }

    vehicle.setPlate(request.plate());
    vehicle.setBrand(request.brand());
    vehicle.setModel(request.model());
    vehicle.setManufactureYear(request.manufactureYear());
    vehicle.setColor(request.color());
    vehicle.setCapacity(request.capacity());
    vehicle.setPhotoFrontUrl(request.photoFrontUrl());
    vehicle.setPhotoSideUrl(request.photoSideUrl());
    vehicle.setPhotoDocumentUrl(request.photoDocumentUrl());

    return mapper.toResponse(vehicleRepository.save(vehicle));
  }

  @Transactional
  public void delete(String token) {
    vehicleRepository.delete(requireByToken(token));
  }

  @Transactional
  public VehicleResponseDTO restore(String token) {
    if (vehicleRepository.existsDeletedByToken(token)) {
      vehicleRepository.restoreByToken(token);
      VehicleModel restored = requireByToken(token);
      return mapper.toResponse(restored);
    }

    if (vehicleRepository.findByToken(token).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, message("vehicle.already_active"));
    }

    throw new ResponseStatusException(HttpStatus.NOT_FOUND, message("vehicle.not_found"));
  }

  private DriverModel getDriverFromEmail(String email) {
    UserModel user =
        userRepository
            .findByEmail(email)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, message("user.account.not_found")));
    return driverRepository
        .findByUserId(user.getId())
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, message("user.driver_profile.not_found")));
  }

  private VehicleModel requireByToken(String token) {
    return vehicleRepository
        .findByToken(token)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, message("vehicle.not_found")));
  }
}
