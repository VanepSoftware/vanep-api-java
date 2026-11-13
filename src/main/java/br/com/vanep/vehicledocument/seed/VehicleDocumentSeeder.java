package br.com.vanep.vehicledocument.seed;

import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driverdocument.seed.DriverDocumentSeeder;
import br.com.vanep.schooltransportcredential.service.SchoolTransportCredentialService;
import br.com.vanep.shared.document.enums.DocumentStatus;
import br.com.vanep.user.repository.UserRepository;
import br.com.vanep.vehicle.model.VehicleModel;
import br.com.vanep.vehicle.repository.VehicleRepository;
import br.com.vanep.vehicledocument.enums.VehicleDocumentType;
import br.com.vanep.vehicledocument.model.VehicleDocumentModel;
import br.com.vanep.vehicledocument.repository.VehicleDocumentRepository;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class VehicleDocumentSeeder {

  private static final Logger log = LoggerFactory.getLogger(VehicleDocumentSeeder.class);

  private record DocumentSeed(VehicleDocumentType type, String numberPrefix, int validMonths) {}

  // Expiry is relative to today so a local database never ends up with expired credentials.
  private static final List<DocumentSeed> SEEDS =
      List.of(
          new DocumentSeed(VehicleDocumentType.CRLV, "RENAVAM", 12),
          new DocumentSeed(VehicleDocumentType.VEHICLE_INSPECTION, "VIST", 6),
          new DocumentSeed(VehicleDocumentType.SCHOOL_VEHICLE_AUTHORIZATION, "AUT-ESC", 12));

  private final VehicleDocumentRepository documents;
  private final VehicleRepository vehicles;
  private final DriverRepository drivers;
  private final UserRepository users;

  public VehicleDocumentSeeder(
      VehicleDocumentRepository documents,
      VehicleRepository vehicles,
      DriverRepository drivers,
      UserRepository users) {
    this.documents = documents;
    this.vehicles = vehicles;
    this.drivers = drivers;
    this.users = users;
  }

  public void seed() {
    LocalDate today = SchoolTransportCredentialService.today();
    List<VehicleModel> seedVans =
        DriverDocumentSeeder.SEED_DRIVER_EMAILS.stream()
            .flatMap(email -> users.findByEmail(email).stream())
            .flatMap(user -> drivers.findByUserId(user.getId()).stream())
            .flatMap(driver -> vehicles.findByDriverId(driver.getId()).stream())
            .toList();
    if (seedVans.isEmpty()) {
      log.info("Seed: vehicle document seed skipped; no seed van found.");
      return;
    }
    List<VehicleDocumentModel> existing =
        documents.findByVehicleIdIn(seedVans.stream().map(van -> van.getId()).toList());
    seedVans.forEach(van -> seedDocuments(van, existing, today));
  }

  void seedDocuments(VehicleModel van, List<VehicleDocumentModel> existing, LocalDate today) {
    for (DocumentSeed seed : SEEDS) {
      boolean hasValidApproved =
          existing.stream()
              .anyMatch(
                  document ->
                      document.getVehicle().getId().equals(van.getId())
                          && document.getDocumentType() == seed.type()
                          && document.getReview().getStatus() == DocumentStatus.APPROVED
                          && !document.getExpiresAt().isBefore(today));
      if (hasValidApproved) {
        continue;
      }
      VehicleDocumentModel document = new VehicleDocumentModel();
      document.setVehicle(van);
      document.setDocumentType(seed.type());
      document.setDocumentNumber(
          "%s-%s/%d".formatted(seed.numberPrefix(), van.getPlate(), today.getYear()));
      document.setExpiresAt(today.plusMonths(seed.validMonths()));
      document.getReview().setStatus(DocumentStatus.APPROVED);
      documents.save(document);
      log.info("Seed: vehicle document {} created for {}.", seed.type(), van.getPlate());
    }
  }
}
