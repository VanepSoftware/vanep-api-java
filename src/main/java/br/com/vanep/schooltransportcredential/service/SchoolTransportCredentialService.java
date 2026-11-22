package br.com.vanep.schooltransportcredential.service;

import br.com.vanep.drivercnh.repository.DriverCnhRepository;
import br.com.vanep.driverdocument.repository.DriverDocumentRepository;
import br.com.vanep.schooltransportcredential.dto.SchoolTransportCredentialsDTO;
import br.com.vanep.schooltransportcredential.dto.SchoolTransportCredentialsResponseDTO;
import br.com.vanep.schooltransportcredential.mapper.SchoolTransportCredentialMapper;
import br.com.vanep.trip.service.TripService;
import br.com.vanep.vehicle.model.VehicleModel;
import br.com.vanep.vehicle.repository.VehicleRepository;
import br.com.vanep.vehicledocument.repository.VehicleDocumentRepository;
import java.time.LocalDate;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SchoolTransportCredentialService {

  private final DriverCnhRepository cnhs;
  private final DriverDocumentRepository driverDocuments;
  private final VehicleDocumentRepository vehicleDocuments;
  private final VehicleRepository vehicles;
  private final SchoolTransportCredentialPolicy policy;
  private final SchoolTransportCredentialMapper mapper;
  private final MessageSource messages;

  public SchoolTransportCredentialService(
      DriverCnhRepository cnhs,
      DriverDocumentRepository driverDocuments,
      VehicleDocumentRepository vehicleDocuments,
      VehicleRepository vehicles,
      SchoolTransportCredentialPolicy policy,
      SchoolTransportCredentialMapper mapper,
      MessageSource messages) {
    this.cnhs = cnhs;
    this.driverDocuments = driverDocuments;
    this.vehicleDocuments = vehicleDocuments;
    this.vehicles = vehicles;
    this.policy = policy;
    this.mapper = mapper;
    this.messages = messages;
  }

  @Transactional(readOnly = true)
  public SchoolTransportCredentialsDTO findCredentials(
      Long driverId, Long vehicleId, LocalDate date) {
    return policy.evaluate(
        cnhs.findByDriverIdAndSupersededAtIsNull(driverId),
        driverDocuments.findByDriverIdAndSupersededAtIsNull(driverId),
        vehicleDocuments.findUnsupersededOfActiveVehicle(driverId, vehicleId),
        date);
  }

  @Transactional(readOnly = true)
  public SchoolTransportCredentialsResponseDTO findTodayForVehicle(String vehicleToken) {
    VehicleModel vehicle =
        vehicles
            .findByToken(vehicleToken)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        messages.getMessage(
                            "vehicle.not_found", null, LocaleContextHolder.getLocale())));
    if (!vehicle.isActive()) {
      throw new ResponseStatusException(
          HttpStatus.UNPROCESSABLE_CONTENT,
          messages.getMessage("vehicle.inactive", null, LocaleContextHolder.getLocale()));
    }
    LocalDate today = today();
    SchoolTransportCredentialsDTO credentials =
        findCredentials(vehicle.getDriver().getId(), vehicle.getId(), today);
    return mapper.toResponse(vehicle.getToken(), today, credentials);
  }

  public static LocalDate today() {
    return LocalDate.now(TripService.SERVICE_ZONE);
  }
}
