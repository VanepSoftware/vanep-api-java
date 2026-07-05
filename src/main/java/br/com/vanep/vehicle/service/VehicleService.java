package br.com.vanep.vehicle.service;

import br.com.vanep.driver.Driver;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.user.User;
import br.com.vanep.user.UserRepository;
import br.com.vanep.vehicle.Vehicle;
import br.com.vanep.vehicle.dto.VehicleRequestDTO;
import br.com.vanep.vehicle.dto.VehicleResponseDTO;
import br.com.vanep.vehicle.mapper.VehicleMapper;
import br.com.vanep.vehicle.repository.VehicleRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class VehicleService {

  private final VehicleRepository vehicleRepository;
  private final DriverRepository driverRepository;
  private final UserRepository userRepository;
  private final VehicleMapper mapper;

  public VehicleService(
      VehicleRepository vehicleRepository,
      DriverRepository driverRepository,
      UserRepository userRepository,
      VehicleMapper mapper) {
    this.vehicleRepository = vehicleRepository;
    this.driverRepository = driverRepository;
    this.userRepository = userRepository;
    this.mapper = mapper;
  }

  @Transactional
  public VehicleResponseDTO create(VehicleRequestDTO request, Jwt jwt, boolean isAdmin) {
    Driver driver;
    if (isAdmin && request.driverToken() != null && !request.driverToken().isBlank()) {
      driver =
          driverRepository
              .findByToken(request.driverToken())
              .orElseThrow(
                  () ->
                      new ResponseStatusException(
                          HttpStatus.NOT_FOUND, "Motorista não encontrado."));
    } else {
      driver = getDriverFromJwt(jwt);
    }

    if (vehicleRepository.existsByPlate(request.plate())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Já existe um veículo cadastrado com esta placa.");
    }

    Vehicle vehicle = new Vehicle();
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

  public List<VehicleResponseDTO> findAll(Jwt jwt, boolean isAdmin) {
    List<Vehicle> vehicles;
    if (isAdmin) {
      vehicles = vehicleRepository.findAll();
    } else {
      Driver driver = getDriverFromJwt(jwt);
      vehicles = vehicleRepository.findByDriverId(driver.getId());
    }
    return vehicles.stream().map(mapper::toResponse).toList();
  }

  public VehicleResponseDTO findByToken(String token) {
    return mapper.toResponse(requireByToken(token));
  }

  @Transactional
  public VehicleResponseDTO update(String token, VehicleRequestDTO request) {
    Vehicle vehicle = requireByToken(token);

    if (!vehicle.getPlate().equalsIgnoreCase(request.plate())
        && vehicleRepository.existsByPlate(request.plate())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Já existe um veículo cadastrado com esta placa.");
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
      Vehicle restored = requireByToken(token);
      return mapper.toResponse(restored);
    }

    if (vehicleRepository.findByToken(token).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "O veículo já está ativo.");
    }

    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Veículo não encontrado.");
  }

  private Driver getDriverFromJwt(Jwt jwt) {
    String email = jwt.getSubject();
    User user =
        userRepository
            .findByEmail(email)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conta não encontrada."));
    return driverRepository
        .findByUserId(user.getId())
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Perfil de motorista não encontrado."));
  }

  private Vehicle requireByToken(String token) {
    return vehicleRepository
        .findByToken(token)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Veículo não encontrado."));
  }
}
