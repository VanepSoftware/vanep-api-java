package br.com.vanep.seed;

import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.drivercnh.model.DriverCnhModel;
import br.com.vanep.drivercnh.repository.DriverCnhRepository;
import br.com.vanep.driverdocument.enums.DriverDocumentType;
import br.com.vanep.driverdocument.model.DriverDocumentModel;
import br.com.vanep.driverdocument.repository.DriverDocumentRepository;
import br.com.vanep.media.enums.MediaSlot;
import br.com.vanep.media.model.MediaFileModel;
import br.com.vanep.media.service.MediaFileService;
import br.com.vanep.schooltransportcredential.service.SchoolTransportCredentialService;
import br.com.vanep.shared.document.enums.DocumentRejectionReason;
import br.com.vanep.shared.document.enums.DocumentStatus;
import br.com.vanep.shared.document.enums.ReviewMethod;
import br.com.vanep.shared.document.model.DocumentReview;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import br.com.vanep.vehicle.model.VehicleModel;
import br.com.vanep.vehicle.repository.VehicleRepository;
import br.com.vanep.vehicledocument.enums.VehicleDocumentType;
import br.com.vanep.vehicledocument.model.VehicleDocumentModel;
import br.com.vanep.vehicledocument.repository.VehicleDocumentRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// The seed driver who sent the onboarding and waits for the admin. Each submission carries a
// file, as the app sends it: the pending ones have only the file, for the reviewer to fill in.
@Component
public class DriverUnderReviewSeeder {

  public static final String EMAIL = "igor.prado@seed.vanep.com.br";
  public static final String PLATE = "VAN4D56";

  // Inside the 30 days of the EXPIRING tab.
  private static final int DAYS_TO_EXPIRY = 20;

  private static final byte[] PDF =
      ("%PDF-1.4\n1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n"
              + "2 0 obj << /Type /Pages /Kids [] /Count 0 >> endobj\n"
              + "trailer << /Root 1 0 R >>\n%%EOF\n")
          .getBytes(StandardCharsets.US_ASCII);

  private static final Logger log = LoggerFactory.getLogger(DriverUnderReviewSeeder.class);

  private final UserRepository users;
  private final DriverRepository drivers;
  private final VehicleRepository vehicles;
  private final DriverCnhRepository cnhs;
  private final DriverDocumentRepository driverDocuments;
  private final VehicleDocumentRepository vehicleDocuments;
  private final MediaFileService media;
  private final String adminEmail;

  public DriverUnderReviewSeeder(
      UserRepository users,
      DriverRepository drivers,
      VehicleRepository vehicles,
      DriverCnhRepository cnhs,
      DriverDocumentRepository driverDocuments,
      VehicleDocumentRepository vehicleDocuments,
      MediaFileService media,
      @Value("${vanep.seed.admin.email:admin@vanep.com.br}") String adminEmail) {
    this.users = users;
    this.drivers = drivers;
    this.vehicles = vehicles;
    this.cnhs = cnhs;
    this.driverDocuments = driverDocuments;
    this.vehicleDocuments = vehicleDocuments;
    this.media = media;
    this.adminEmail = adminEmail;
  }

  public void seed() {
    Optional<DriverModel> found =
        users.findByEmail(EMAIL).flatMap(user -> drivers.findByUserId(user.getId()));
    if (found.isEmpty()) {
      log.info("Seed: driver under review skipped; driver not found ({}).", EMAIL);
      return;
    }
    DriverModel driver = found.get();
    seedCnh(driver);
    if (!driverDocuments.findByDriverIdAndSupersededAtIsNull(driver.getId()).isEmpty()) {
      return;
    }
    UserModel admin = users.findByEmail(adminEmail).orElse(null);
    LocalDate today = SchoolTransportCredentialService.today();
    seedDriverDocuments(driver, admin, today);
    vehicles.findByDriverId(driver.getId()).stream()
        .filter(van -> PLATE.equals(van.getPlate()))
        .findFirst()
        .ifPresent(van -> seedVehicleDocuments(van, admin, today));
    log.info("Seed: submissions of the driver under review created ({}).", EMAIL);
  }

  private void seedCnh(DriverModel driver) {
    if (!cnhs.findByDriverIdAndSupersededAtIsNull(driver.getId()).isEmpty()) {
      return;
    }
    DriverCnhModel cnh = new DriverCnhModel();
    cnh.setDriver(driver);
    cnh.setFile(file(MediaSlot.DRIVER_CNH_FILE, driver.getToken(), "cnh"));
    cnhs.save(cnh);
  }

  private void seedDriverDocuments(DriverModel driver, UserModel admin, LocalDate today) {
    driverDocuments.save(driverDocument(driver, DriverDocumentType.SCHOOL_DRIVER_COURSE));

    DriverDocumentModel record = driverDocument(driver, DriverDocumentType.CRIMINAL_RECORD);
    reject(record.getReview(), admin, "A foto da certidão está desfocada.");
    driverDocuments.save(record);

    DriverDocumentModel authorization =
        driverDocument(driver, DriverDocumentType.MUNICIPAL_AUTHORIZATION);
    authorization.setDocumentNumber("TE-0004/%d".formatted(today.getYear()));
    authorization.setExpiresAt(today.plusDays(DAYS_TO_EXPIRY));
    approve(authorization.getReview(), admin);
    driverDocuments.save(authorization);
  }

  private void seedVehicleDocuments(VehicleModel van, UserModel admin, LocalDate today) {
    vehicleDocuments.save(vehicleDocument(van, VehicleDocumentType.CRLV));
    vehicleDocuments.save(vehicleDocument(van, VehicleDocumentType.SCHOOL_VEHICLE_AUTHORIZATION));

    VehicleDocumentModel inspection = vehicleDocument(van, VehicleDocumentType.VEHICLE_INSPECTION);
    inspection.setDocumentNumber("VIST-%s/%d".formatted(van.getPlate(), today.getYear()));
    inspection.setExpiresAt(today.plusDays(DAYS_TO_EXPIRY));
    approve(inspection.getReview(), admin);
    vehicleDocuments.save(inspection);
  }

  private DriverDocumentModel driverDocument(DriverModel driver, DriverDocumentType type) {
    DriverDocumentModel document = new DriverDocumentModel();
    document.setDriver(driver);
    document.setDocumentType(type);
    document.setFile(file(MediaSlot.DRIVER_DOCUMENT_FILE, driver.getToken(), type.name()));
    return document;
  }

  private VehicleDocumentModel vehicleDocument(VehicleModel van, VehicleDocumentType type) {
    VehicleDocumentModel document = new VehicleDocumentModel();
    document.setVehicle(van);
    document.setDocumentType(type);
    document.setFile(file(MediaSlot.VEHICLE_DOCUMENT_FILE, van.getToken(), type.name()));
    return document;
  }

  private MediaFileModel file(MediaSlot slot, String ownerToken, String name) {
    return media.store(slot, ownerToken, PDF, name.toLowerCase() + ".pdf");
  }

  private static void approve(DocumentReview review, UserModel admin) {
    review.setStatus(DocumentStatus.APPROVED);
    review.setReviewMethod(ReviewMethod.MANUAL);
    review.setReviewedBy(admin);
    review.setReviewedAt(Instant.now());
  }

  private static void reject(DocumentReview review, UserModel admin, String reason) {
    review.setStatus(DocumentStatus.REJECTED);
    review.setRejectionReasonCode(DocumentRejectionReason.ILLEGIBLE);
    review.setRejectionReason(reason);
    review.setReviewMethod(ReviewMethod.MANUAL);
    review.setReviewedBy(admin);
    review.setReviewedAt(Instant.now());
  }
}
