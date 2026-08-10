package br.com.vanep.driverdocument.seed;

import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.driverdocument.enums.DocumentStatusEnum;
import br.com.vanep.driverdocument.enums.DocumentTypeEnum;
import br.com.vanep.driverdocument.model.DriverDocumentModel;
import br.com.vanep.driverdocument.repository.DriverDocumentRepository;
import br.com.vanep.user.UserRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
public class DriverDocumentSeeder {

  private static final Logger log = LoggerFactory.getLogger(DriverDocumentSeeder.class);
  private static final String SEED_DRIVER_EMAIL = "fabio.teixeira@seed.vanep.com.br";

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
    Optional<DriverModel> driverOpt = resolveSeedDriver();
    if (driverOpt.isEmpty()) {
      log.info(
          "Seed: driver document seed skipped; seed driver not found ({}).", SEED_DRIVER_EMAIL);
      return;
    }

    DriverModel driver = driverOpt.get();
    if (!documents.findByDriverId(driver.getId(), Pageable.unpaged()).isEmpty()) {
      return;
    }

    DriverDocumentModel doc1 = new DriverDocumentModel();
    doc1.setDriver(driver);
    doc1.setDocumentType(DocumentTypeEnum.CRLV);
    doc1.setFileUrl("https://storage.vanep.com.br/docs/crlv_fabio.pdf");
    doc1.setExpiresAt(LocalDate.of(2026, 12, 31));
    doc1.setStatus(DocumentStatusEnum.APPROVED);
    documents.save(doc1);

    DriverDocumentModel doc2 = new DriverDocumentModel();
    doc2.setDriver(driver);
    doc2.setDocumentType(DocumentTypeEnum.CRIMINAL_RECORD);
    doc2.setFileUrl("https://storage.vanep.com.br/docs/criminal_fabio.pdf");
    doc2.setExpiresAt(LocalDate.of(2026, 10, 15));
    doc2.setStatus(DocumentStatusEnum.PENDING);
    documents.save(doc2);

    log.info("Seed: driver documents created for {}.", SEED_DRIVER_EMAIL);
  }

  private Optional<DriverModel> resolveSeedDriver() {
    return users.findByEmail(SEED_DRIVER_EMAIL).flatMap(user -> drivers.findByUserId(user.getId()));
  }
}
