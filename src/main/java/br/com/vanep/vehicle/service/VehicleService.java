package br.com.vanep.vehicle.service;

import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.shared.error.ApiProblemException;
import br.com.vanep.unlinkedpassenger.repository.UnlinkedPassengerRepository;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import br.com.vanep.user.service.UserService;
import br.com.vanep.vehicle.dto.VehicleRequestDTO;
import br.com.vanep.vehicle.dto.VehicleResponseDTO;
import br.com.vanep.vehicle.mapper.VehicleMapper;
import br.com.vanep.vehicle.model.VehicleModel;
import br.com.vanep.vehicle.repository.VehicleRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VehicleService {

  private final VehicleRepository vehicleRepository;
  private final UnlinkedPassengerRepository unlinkedPassengers;
  private final DriverRepository driverRepository;
  private final UserRepository userRepository;
  private final UserService userService;
  private final VehicleMapper mapper;

  public VehicleService(
      VehicleRepository vehicleRepository,
      UnlinkedPassengerRepository unlinkedPassengers,
      DriverRepository driverRepository,
      UserRepository userRepository,
      UserService userService,
      VehicleMapper mapper) {
    this.vehicleRepository = vehicleRepository;
    this.unlinkedPassengers = unlinkedPassengers;
    this.driverRepository = driverRepository;
    this.userRepository = userRepository;
    this.userService = userService;
    this.mapper = mapper;
  }

  @Transactional
  public VehicleResponseDTO create(VehicleRequestDTO request, String callerEmail) {
    UserModel caller =
        userRepository
            .findByEmail(callerEmail)
            .orElseThrow(() -> ApiProblemException.notFound("user.account.not_found"));

    DriverModel driver;
    if (caller.getType() == UserType.ADMIN
        && request.driverToken() != null
        && !request.driverToken().isBlank()) {
      driver =
          driverRepository
              .findByToken(request.driverToken())
              .orElseThrow(() -> ApiProblemException.notFound("vehicle.driver.not_found"));
    } else {
      driver =
          driverRepository
              .findByUserId(caller.getId())
              .orElseThrow(() -> ApiProblemException.notFound("user.driver_profile.not_found"));
    }

    return createFor(driver, request);
  }

  @Transactional
  public VehicleResponseDTO createMine(String callerUid, VehicleRequestDTO request) {
    return createFor(requireCallerDriver(callerUid), request);
  }

  @Transactional(readOnly = true)
  public List<VehicleResponseDTO> findMine(String callerUid) {
    return vehicleRepository.findByDriverId(requireCallerDriver(callerUid).getId()).stream()
        .map(mapper::toResponse)
        .toList();
  }

  VehicleResponseDTO createFor(DriverModel driver, VehicleRequestDTO request) {
    String plate = normalizePlate(request.plate());
    if (vehicleRepository.existsByPlate(plate)) {
      throw ApiProblemException.conflict("vehicle.plate.duplicate");
    }

    VehicleModel vehicle = new VehicleModel();
    vehicle.setDriver(driver);
    vehicle.setPlate(plate);
    vehicle.setBrand(request.brand());
    vehicle.setModel(request.model());
    vehicle.setManufactureYear(request.manufactureYear());
    vehicle.setColor(request.color());
    vehicle.setCapacity(request.capacity());

    return mapper.toResponse(vehicleRepository.save(vehicle));
  }

  DriverModel requireCallerDriver(String callerUid) {
    UserModel user = userService.requireByTokenAndType(callerUid, UserType.DRIVER);
    return driverRepository
        .findByUserId(user.getId())
        .orElseThrow(() -> ApiProblemException.notFound("user.driver_profile.not_found"));
  }

  public List<VehicleResponseDTO> findAll(String callerEmail) {
    UserModel caller =
        userRepository
            .findByEmail(callerEmail)
            .orElseThrow(() -> ApiProblemException.notFound("user.account.not_found"));

    List<VehicleModel> vehicles;
    if (caller.getType() == UserType.ADMIN) {
      vehicles = vehicleRepository.findAll();
    } else {
      DriverModel driver =
          driverRepository
              .findByUserId(caller.getId())
              .orElseThrow(() -> ApiProblemException.notFound("user.driver_profile.not_found"));
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

    String plate = normalizePlate(request.plate());
    if (!vehicle.getPlate().equals(plate) && vehicleRepository.existsByPlate(plate)) {
      throw ApiProblemException.conflict("vehicle.plate.duplicate");
    }

    vehicle.setPlate(plate);
    vehicle.setBrand(request.brand());
    vehicle.setModel(request.model());
    vehicle.setManufactureYear(request.manufactureYear());
    vehicle.setColor(request.color());
    vehicle.setCapacity(request.capacity());

    return mapper.toResponse(vehicleRepository.save(vehicle));
  }

  @Transactional
  public void delete(String token) {
    VehicleModel vehicle = requireByToken(token);
    driverRepository.lockById(vehicle.getDriver().getId());
    unlinkedPassengers.clearVehicleId(vehicle.getId());
    vehicleRepository.delete(vehicle);
  }

  @Transactional
  public VehicleResponseDTO restore(String token) {
    if (vehicleRepository.existsDeletedByToken(token)) {
      vehicleRepository.restoreByToken(token);
      VehicleModel restored = requireByToken(token);
      return mapper.toResponse(restored);
    }

    if (vehicleRepository.findByToken(token).isPresent()) {
      throw ApiProblemException.conflict("vehicle.already_active");
    }

    throw ApiProblemException.notFound("vehicle.not_found");
  }

  private VehicleModel requireByToken(String token) {
    return vehicleRepository
        .findByToken(token)
        .orElseThrow(() -> ApiProblemException.notFound("vehicle.not_found"));
  }

  // A plate is stored in one spelling so "ABC-1D23" and "abc1d23" are the same van.
  public static String normalizePlate(String plate) {
    return plate == null ? null : plate.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
  }
}
