package br.com.vanep.driver.service;

import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.dto.DriverProfileResponseDTO;
import br.com.vanep.driver.mapper.DriverMapper;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.vehicle.repository.VehicleRepository;
import java.util.List;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DriverProfileService {

  private final DriverRepository drivers;
  private final VehicleRepository vehicles;
  private final DriverSearchService searchService;
  private final DriverMapper mapper;
  private final MessageSource messages;

  public DriverProfileService(
      DriverRepository drivers,
      VehicleRepository vehicles,
      DriverSearchService searchService,
      DriverMapper mapper,
      MessageSource messages) {
    this.drivers = drivers;
    this.vehicles = vehicles;
    this.searchService = searchService;
    this.mapper = mapper;
    this.messages = messages;
  }

  // A driver the search would not show is not found here either, so the profile never
  // reveals a pending, rejected or inactive account.
  @Transactional(readOnly = true)
  public DriverProfileResponseDTO findProfile(String token) {
    DriverModel driver =
        drivers
            .findSearchableByToken(token)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        messages.getMessage(
                            "driver.not_found", null, LocaleContextHolder.getLocale())));

    List<String> serviceAreas =
        searchService
            .findAreaNames(List.of(driver.getId()))
            .getOrDefault(driver.getId(), List.of());
    return mapper.toProfile(
        driver, serviceAreas, vehicles.findActiveWithPhotosByDriverId(driver.getId()));
  }
}
