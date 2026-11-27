package br.com.vanep.driver.service;

import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.dto.DriverProfileResponseDTO;
import br.com.vanep.driver.mapper.DriverMapper;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.shared.error.ApiProblemException;
import br.com.vanep.vehicle.repository.VehicleRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DriverProfileService {

  private final DriverRepository drivers;
  private final VehicleRepository vehicles;
  private final DriverSearchService searchService;
  private final DriverMapper mapper;

  public DriverProfileService(
      DriverRepository drivers,
      VehicleRepository vehicles,
      DriverSearchService searchService,
      DriverMapper mapper) {
    this.drivers = drivers;
    this.vehicles = vehicles;
    this.searchService = searchService;
    this.mapper = mapper;
  }

  // A driver the search would not show is not found here either, so the profile never
  // reveals a pending, rejected or inactive account.
  @Transactional(readOnly = true)
  public DriverProfileResponseDTO findProfile(String token) {
    DriverModel driver =
        drivers
            .findSearchableByToken(token)
            .orElseThrow(() -> ApiProblemException.notFound("driver.not_found"));

    List<String> serviceAreas =
        searchService
            .findAreaNames(List.of(driver.getId()))
            .getOrDefault(driver.getId(), List.of());
    return mapper.toProfile(
        driver, serviceAreas, vehicles.findActiveWithPhotosByDriverId(driver.getId()));
  }
}
