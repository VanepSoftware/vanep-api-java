package br.com.vanep.driverservicearea.service;

import br.com.vanep.district.model.DistrictModel;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.driverservicearea.dto.DriverServiceAreaRequestDTO;
import br.com.vanep.driverservicearea.dto.DriverServiceAreaResponseDTO;
import br.com.vanep.driverservicearea.model.DriverServiceAreaModel;
import br.com.vanep.driverservicearea.repository.DriverServiceAreaRepository;
import br.com.vanep.location.dto.ResolvedLocationChainDTO;
import br.com.vanep.location.service.LocationResolverService;
import br.com.vanep.places.client.PlacesClient;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.service.UserService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Regiões de atuação do motorista autenticado. */
@Service
public class DriverServiceAreaService {

  private final PlacesClient places;
  private final LocationResolverService resolver;
  private final DriverServiceAreaRepository areas;
  private final DriverRepository drivers;
  private final UserService users;
  private final MessageSource messages;

  public DriverServiceAreaService(
      PlacesClient places,
      LocationResolverService resolver,
      DriverServiceAreaRepository areas,
      DriverRepository drivers,
      UserService users,
      MessageSource messages) {
    this.places = places;
    this.resolver = resolver;
    this.areas = areas;
    this.drivers = drivers;
    this.users = users;
    this.messages = messages;
  }

  @Transactional(readOnly = true)
  public List<DriverServiceAreaResponseDTO> findMyAreas(String callerUid) {
    return areas.findByDriverId(requireDriver(callerUid).getId()).stream()
        .map(this::toResponse)
        .toList();
  }

  /**
   * Substitui o conjunto inteiro. Substituição e não merge porque "onde eu atendo" é uma declaração
   * completa: remover uma região tem de ser possível sem endpoint de exclusão por item.
   */
  @Transactional
  public List<DriverServiceAreaResponseDTO> replaceMyAreas(
      String callerUid, DriverServiceAreaRequestDTO request) {
    DriverModel driver = requireDriver(callerUid);

    // Resolve tudo antes de apagar qualquer coisa: se um place da lista for
    // recusado, o motorista não pode terminar sem região nenhuma.
    Map<String, DriverServiceAreaModel> resolved = new LinkedHashMap<>();
    for (DriverServiceAreaRequestDTO.Item item : request.areas()) {
      ResolvedLocationChainDTO chain =
          resolver.resolveAndPersist(places.findPlaceDetails(item.placeId(), item.sessionToken()));

      if (!ServiceAreaGranularityPolicy.isAcceptable(chain)) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, message("driver_service_area.district_required"));
      }

      DriverServiceAreaModel area = new DriverServiceAreaModel();
      area.setDriver(driver);
      area.setCity(chain.city());
      area.setDistrict(chain.deepestDistrict().orElse(null));
      // Dois places distintos podem resolver para o mesmo nó (D2). Deduplicar aqui
      // evita esbarrar no índice único e devolver 500 para um pedido legítimo.
      resolved.putIfAbsent(dedupeKey(area), area);
    }

    areas.deleteAll(areas.findByDriverId(driver.getId()));
    areas.flush();

    List<DriverServiceAreaResponseDTO> saved = new ArrayList<>();
    for (DriverServiceAreaModel area : resolved.values()) {
      saved.add(toResponse(areas.save(area)));
    }
    return saved;
  }

  String dedupeKey(DriverServiceAreaModel area) {
    Long districtId = area.getDistrict() == null ? null : area.getDistrict().getId();
    return area.getCity().getId() + ":" + districtId;
  }

  private DriverModel requireDriver(String callerUid) {
    UserModel user = users.requireByTokenAndType(callerUid, UserType.DRIVER);
    return drivers
        .findByUserId(user.getId())
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, message("user.driver_profile.not_found")));
  }

  DriverServiceAreaResponseDTO toResponse(DriverServiceAreaModel area) {
    DistrictModel district = area.getDistrict();
    return new DriverServiceAreaResponseDTO(
        area.getToken(),
        district == null ? area.getCity().getName() : district.getName(),
        district == null ? null : district.getToken(),
        area.getCity().getName(),
        area.getCity().getToken(),
        area.getCity().getState().getUf(),
        district == null);
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }
}
