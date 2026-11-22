package br.com.vanep.driverdocument.seed;

import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.driverdocument.enums.DriverDocumentType;
import br.com.vanep.driverdocument.model.DriverDocumentModel;
import br.com.vanep.driverdocument.repository.DriverDocumentRepository;
import br.com.vanep.schooltransportcredential.service.SchoolTransportCredentialService;
import br.com.vanep.shared.document.enums.DocumentStatus;
import br.com.vanep.shared.document.enums.ReviewMethod;
import br.com.vanep.user.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DriverDocumentSeeder {

  public static final List<String> SEED_DRIVER_EMAILS =
      List.of(
          "fabio.teixeira@seed.vanep.com.br",
          "gustavo.santos@seed.vanep.com.br",
          "helena.costa@seed.vanep.com.br");

  private static final Logger log = LoggerFactory.getLogger(DriverDocumentSeeder.class);

  private record DocumentSeed(DriverDocumentType type, String numberPrefix, int validMonths) {}

  // Expiry is relative to today so a local database never ends up with expired credentials.
  private static final List<DocumentSeed> SEEDS =
      List.of(
          new DocumentSeed(DriverDocumentType.SCHOOL_DRIVER_COURSE, "CURSO-ESC", 60),
          new DocumentSeed(DriverDocumentType.CRIMINAL_RECORD, "CERT-CRIM", 3),
          new DocumentSeed(DriverDocumentType.MUNICIPAL_AUTHORIZATION, "TE", 12));

  private final DriverDocumentRepository documents;
  private final DriverRepository drivers;
  private final UserRepository users;

  public DriverDocumentSeeder(
      DriverDocumentRepository documents, DriverRepository drivers, UserRepository users) {
    this.documents = documents;
    this.drivers = drivers;
    this.users = users;
  }

  public void seed() {
    LocalDate today = SchoolTransportCredentialService.today();
    for (int index = 0; index < SEED_DRIVER_EMAILS.size(); index++) {
      String email = SEED_DRIVER_EMAILS.get(index);
      Optional<DriverModel> driver = resolveDriver(email);
      if (driver.isEmpty()) {
        log.info("Seed: driver document seed skipped; seed driver not found ({}).", email);
        continue;
      }
      seedDocuments(driver.get(), index + 1, today);
    }
  }

  void seedDocuments(DriverModel driver, int sequence, LocalDate today) {
    List<DriverDocumentModel> existing =
        documents.findByDriverIdAndSupersededAtIsNull(driver.getId());
    for (DocumentSeed seed : SEEDS) {
      boolean hasValidApproved =
          existing.stream()
              .anyMatch(
                  document ->
                      document.getDocumentType() == seed.type()
                          && document.isActive()
                          && document.getReview().getStatus() == DocumentStatus.APPROVED
                          && document.getExpiresAt() != null
                          && !document.getExpiresAt().isBefore(today));
      if (hasValidApproved) {
        continue;
      }
      DriverDocumentModel document = new DriverDocumentModel();
      document.setDriver(driver);
      document.setDocumentType(seed.type());
      document.setDocumentNumber(
          "%s-%04d/%d".formatted(seed.numberPrefix(), sequence, today.getYear()));
      document.setExpiresAt(today.plusMonths(seed.validMonths()));
      document.getReview().setStatus(DocumentStatus.APPROVED);
      document.getReview().setReviewMethod(ReviewMethod.MANUAL);
      document.getReview().setReviewedAt(Instant.now());
      documents.save(document);
      log.info("Seed: driver document {} created for driver {}.", seed.type(), driver.getToken());
    }
  }

  private Optional<DriverModel> resolveDriver(String email) {
    return users.findByEmail(email).flatMap(user -> drivers.findByUserId(user.getId()));
  }
}
