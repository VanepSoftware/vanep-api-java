package br.com.vanep.schooltransportcredential.service;

import br.com.vanep.drivercnh.repository.DriverCnhRepository;
import br.com.vanep.driverdocument.repository.DriverDocumentRepository;
import br.com.vanep.schooltransportcredential.dto.SchoolTransportCredentialsDTO;
import br.com.vanep.schooltransportcredential.dto.SchoolTransportCredentialsResponseDTO;
import br.com.vanep.schooltransportcredential.mapper.SchoolTransportCredentialMapper;
import br.com.vanep.shared.error.ApiProblemException;
import br.com.vanep.trip.service.TripService;
import br.com.vanep.vehicle.model.VehicleModel;
import br.com.vanep.vehicle.repository.VehicleRepository;
import br.com.vanep.vehicledocument.repository.VehicleDocumentRepository;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchoolTransportCredentialService {

  private final DriverCnhRepository cnhs;
  private final DriverDocumentRepository driverDocuments;
  private final VehicleDocumentRepository vehicleDocuments;
  private final VehicleRepository vehicles;
  private final SchoolTransportCredentialPolicy policy;
  private final SchoolTransportCredentialMapper mapper;

  public SchoolTransportCredentialService(
      DriverCnhRepository cnhs,
      DriverDocumentRepository driverDocuments,
      VehicleDocumentRepository vehicleDocuments,
      VehicleRepository vehicles,
      SchoolTransportCredentialPolicy policy,
      SchoolTransportCredentialMapper mapper) {
    this.cnhs = cnhs;
    this.driverDocuments = driverDocuments;
    this.vehicleDocuments = vehicleDocuments;
    this.vehicles = vehicles;
    this.policy = policy;
    this.mapper = mapper;
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
            .orElseThrow(() -> ApiProblemException.notFound("vehicle.not_found"));
    if (!vehicle.isActive()) {
      throw ApiProblemException.unprocessable("vehicle.inactive");
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
