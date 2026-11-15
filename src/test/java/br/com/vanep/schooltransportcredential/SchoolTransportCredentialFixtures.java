package br.com.vanep.schooltransportcredential;

import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.drivercnh.model.DriverCnhModel;
import br.com.vanep.drivercnh.repository.DriverCnhRepository;
import br.com.vanep.driverdocument.enums.DriverDocumentType;
import br.com.vanep.driverdocument.model.DriverDocumentModel;
import br.com.vanep.driverdocument.repository.DriverDocumentRepository;
import br.com.vanep.shared.document.enums.DocumentStatus;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import br.com.vanep.vehicle.model.VehicleModel;
import br.com.vanep.vehicle.repository.VehicleRepository;
import br.com.vanep.vehicledocument.enums.VehicleDocumentType;
import br.com.vanep.vehicledocument.model.VehicleDocumentModel;
import br.com.vanep.vehicledocument.repository.VehicleDocumentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.context.ApplicationContext;

public class SchoolTransportCredentialFixtures {

  public static final LocalDate VALID_UNTIL = LocalDate.now().plusYears(1);

  private final UserRepository users;
  private final DriverRepository drivers;
  private final VehicleRepository vehicles;
  private final DriverCnhRepository cnhs;
  private final DriverDocumentRepository driverDocuments;
  private final VehicleDocumentRepository vehicleDocuments;

  public SchoolTransportCredentialFixtures(ApplicationContext context) {
    users = context.getBean(UserRepository.class);
    drivers = context.getBean(DriverRepository.class);
    vehicles = context.getBean(VehicleRepository.class);
    cnhs = context.getBean(DriverCnhRepository.class);
    driverDocuments = context.getBean(DriverDocumentRepository.class);
    vehicleDocuments = context.getBean(VehicleDocumentRepository.class);
  }

  public DriverModel createDriver(String email, String cpf) {
    UserModel user = new UserModel();
    user.setType(UserType.DRIVER);
    user.setName("Motorista");
    user.setEmail(email);
    user.setDocument(cpf);
    user.setVerified(true);
    user.setTermsAcceptedAt(Instant.now());
    user = users.save(user);

    DriverModel driver = new DriverModel();
    driver.setUser(user);
    driver.setBasePrice(new BigDecimal("100.00"));
    return drivers.save(driver);
  }

  public VehicleModel createVehicle(DriverModel driver, String plate) {
    VehicleModel vehicle = new VehicleModel();
    vehicle.setDriver(driver);
    vehicle.setPlate(plate);
    vehicle.setBrand("Renault");
    vehicle.setModel("Master");
    vehicle.setManufactureYear(2022);
    vehicle.setColor("Branca");
    vehicle.setCapacity(15);
    return vehicles.save(vehicle);
  }

  public DriverCnhModel createCnh(DriverModel driver, String registrationNumber, String category) {
    DriverCnhModel cnh = new DriverCnhModel();
    cnh.setDriver(driver);
    cnh.setRegistrationNumber(registrationNumber);
    cnh.setCategory(category);
    cnh.setIssueDate(LocalDate.of(2015, 3, 10));
    cnh.setValidUntil(VALID_UNTIL);
    cnh.getReview().setStatus(DocumentStatus.APPROVED);
    return cnhs.save(cnh);
  }

  public DriverDocumentModel createDriverDocument(
      DriverModel driver, DriverDocumentType type, String number, DocumentStatus status) {
    DriverDocumentModel document = new DriverDocumentModel();
    document.setDriver(driver);
    document.setDocumentType(type);
    document.setDocumentNumber(number);
    document.getReview().setStatus(status);
    return driverDocuments.save(document);
  }

  public VehicleDocumentModel createVehicleDocument(
      VehicleModel vehicle, VehicleDocumentType type, String number, DocumentStatus status) {
    VehicleDocumentModel document = new VehicleDocumentModel();
    document.setVehicle(vehicle);
    document.setDocumentType(type);
    document.setDocumentNumber(number);
    document.setExpiresAt(VALID_UNTIL);
    document.getReview().setStatus(status);
    return vehicleDocuments.save(document);
  }

  public void approveTheDriversDocuments(DriverModel driver, String cnhNumber) {
    createCnh(driver, cnhNumber, "AD");
    createDriverDocument(
        driver, DriverDocumentType.SCHOOL_DRIVER_COURSE, "CURSO-1", DocumentStatus.APPROVED);
    createDriverDocument(
        driver, DriverDocumentType.CRIMINAL_RECORD, "CERT-1", DocumentStatus.APPROVED);
  }

  public void approveTheVansDocuments(VehicleModel vehicle) {
    createVehicleDocument(vehicle, VehicleDocumentType.CRLV, "RENAVAM-1", DocumentStatus.APPROVED);
    createVehicleDocument(
        vehicle, VehicleDocumentType.VEHICLE_INSPECTION, null, DocumentStatus.APPROVED);
    createVehicleDocument(
        vehicle,
        VehicleDocumentType.SCHOOL_VEHICLE_AUTHORIZATION,
        "AUT-118",
        DocumentStatus.APPROVED);
  }
}
