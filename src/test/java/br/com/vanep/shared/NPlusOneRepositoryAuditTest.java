package br.com.vanep.shared;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.assistant.model.AssistantModel;
import br.com.vanep.assistant.repository.AssistantRepository;
import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.clientdriver.enums.LinkOrigin;
import br.com.vanep.clientdriver.model.ClientDriverModel;
import br.com.vanep.clientdriver.repository.ClientDriverRepository;
import br.com.vanep.contract.enums.ContractStatus;
import br.com.vanep.contract.model.ContractModel;
import br.com.vanep.contract.repository.ContractRepository;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.drivercnh.model.DriverCnhModel;
import br.com.vanep.drivercnh.repository.DriverCnhRepository;
import br.com.vanep.driverdocument.enums.DocumentStatusEnum;
import br.com.vanep.driverdocument.enums.DocumentTypeEnum;
import br.com.vanep.driverdocument.model.DriverDocumentModel;
import br.com.vanep.driverdocument.repository.DriverDocumentRepository;
import br.com.vanep.media.enums.MediaVisibility;
import br.com.vanep.media.enums.StorageProvider;
import br.com.vanep.media.model.MediaFileModel;
import br.com.vanep.media.repository.MediaFileRepository;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import br.com.vanep.vehicle.model.VehicleModel;
import br.com.vanep.vehicle.repository.VehicleRepository;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class NPlusOneRepositoryAuditTest {

  @Autowired private EntityManagerFactory entityManagerFactory;
  @Autowired private VehicleRepository vehicles;
  @Autowired private DriverDocumentRepository documents;
  @Autowired private DriverCnhRepository cnhs;
  @Autowired private AssistantRepository assistants;
  @Autowired private ContractRepository contracts;
  @Autowired private ClientDriverRepository links;
  @Autowired private DriverRepository drivers;
  @Autowired private ClientRepository clients;
  @Autowired private UserRepository users;
  @Autowired private MediaFileRepository media;
  @Autowired private PlatformTransactionManager transactionManager;

  private DriverModel driver;
  private ClientDriverModel link;
  private TransactionTemplate readOnly;

  @BeforeEach
  void setUp() {
    readOnly = new TransactionTemplate(transactionManager);
    readOnly.setReadOnly(true);
    UserModel driverUser = saveUser("driver@vanep.com", UserType.DRIVER, "driver-user");
    driver = new DriverModel();
    driver.setUser(driverUser);
    driver.setBasePrice(new BigDecimal("80.00"));
    driver = drivers.save(driver);

    UserModel clientUser = saveUser("client@vanep.com", UserType.CLIENT, "client-user");
    ClientModel client = new ClientModel();
    client.setUser(clientUser);
    client = clients.save(client);

    link = new ClientDriverModel();
    link.setClient(client);
    link.setDriver(driver);
    link.setOrigin(LinkOrigin.CLIENT_SEARCH);
    link = links.save(link);
  }

  @Test
  void listQueriesDoNotGrowWithRowCount(TestReporter reporter) {
    SqlStatementCounter counter = new SqlStatementCounter(entityManagerFactory);
    List<String> hotspots = new ArrayList<>();

    recordHotspot(
        counter,
        hotspots,
        "VehicleRepository.findByDriverId (touch driver.user + 3 photos)",
        () -> createVehicle(0),
        () -> touchVehicles(vehicles.findByDriverId(driver.getId())),
        () -> {
          createVehicle(1);
          createVehicle(2);
          createVehicle(3);
        },
        reporter);

    recordHotspot(
        counter,
        hotspots,
        "DriverDocumentRepository.findByDriverId (touch driver.user + file + reviewer)",
        () -> createDocument(0),
        () -> touchDocuments(documents.findByDriverId(driver.getId())),
        () -> {
          createDocument(1);
          createDocument(2);
          createDocument(3);
        },
        reporter);

    recordHotspot(
        counter,
        hotspots,
        "DriverCnhRepository.findByDriverId (touch driver.user)",
        () -> createCnh(0),
        () -> touchCnhs(cnhs.findByDriverId(driver.getId())),
        () -> {
          createCnh(1);
          createCnh(2);
          createCnh(3);
        },
        reporter);

    recordHotspot(
        counter,
        hotspots,
        "DriverDocumentRepository.findByDriverId(Pageable) (touch driver.user + file + reviewer)",
        () -> {},
        () ->
            touchDocuments(
                documents.findByDriverId(driver.getId(), PageRequest.of(0, 20)).getContent()),
        () -> {
          createDocument(4);
          createDocument(5);
          createDocument(6);
        },
        reporter);

    recordHotspot(
        counter,
        hotspots,
        "DriverDocumentRepository.findAll(Pageable) — admin, one driver per row",
        () -> createDocumentForNewDriver(0),
        () -> touchDocuments(documents.findAll(PageRequest.of(0, 50)).getContent()),
        () -> {
          createDocumentForNewDriver(1);
          createDocumentForNewDriver(2);
          createDocumentForNewDriver(3);
        },
        reporter);

    recordHotspot(
        counter,
        hotspots,
        "AssistantRepository.findByDriverId (touch user + driver.user + photo)",
        () -> createAssistant(0),
        () -> touchAssistants(assistants.findByDriverId(driver.getId())),
        () -> {
          createAssistant(1);
          createAssistant(2);
          createAssistant(3);
        },
        reporter);

    recordHotspot(
        counter,
        hotspots,
        "ContractRepository.findByClientDriverId (touch link.client.user + link.driver.user)",
        () -> createContract(0),
        () -> touchContracts(contracts.findByClientDriverId(link.getId())),
        () -> {
          createContract(1);
          createContract(2);
          createContract(3);
        },
        reporter);

    List<Long> clientIds = new ArrayList<>();
    recordHotspot(
        counter,
        hotspots,
        "ClientRepository.findTokensByIdIn — admin dependent list, one client per row",
        () -> clientIds.add(createClient(0)),
        () -> clients.findTokensByIdIn(List.copyOf(clientIds)).forEach(row -> row.getToken()),
        () -> {
          clientIds.add(createClient(1));
          clientIds.add(createClient(2));
          clientIds.add(createClient(3));
        },
        reporter);

    hotspots.forEach(hotspot -> reporter.publishEntry("n-plus-one-hotspot", hotspot));

    assertThat(hotspots)
        .as("Listas cuja contagem de statements cresce com o número de linhas (N+1, regra 17)")
        .isEmpty();
  }

  private void recordHotspot(
      SqlStatementCounter counter,
      List<String> hotspots,
      String label,
      Runnable seedFirst,
      Runnable exercise,
      Runnable seedThreeMore,
      TestReporter reporter) {
    seedFirst.run();
    // Inside a read-only transaction, like the services, so lazy associations are touched too.
    Runnable inTransaction = () -> readOnly.executeWithoutResult(status -> exercise.run());
    long before = counter.countStatements(inTransaction);
    seedThreeMore.run();
    long after = counter.countStatements(inTransaction);
    String line = label + " — statements " + before + " → " + after + " after 3 more rows";
    reporter.publishEntry("n-plus-one-probe", line);
    System.out.println("N+1-AUDIT " + line);
    if (after > before) {
      hotspots.add(line);
    }
  }

  private MediaFileModel saveMedia() {
    MediaFileModel file = new MediaFileModel();
    file.setProvider(StorageProvider.LOCAL);
    file.setObjectKey("audit/" + System.nanoTime());
    file.setMimeType("image/jpeg");
    file.setSizeBytes(1L);
    file.setVisibility(MediaVisibility.PRIVATE);
    return media.save(file);
  }

  private UserModel saveUser(String email, UserType type, String name) {
    UserModel user = new UserModel();
    user.setType(type);
    user.setName(name);
    user.setEmail(email);
    user.setDocument("3905334470" + Math.abs(email.hashCode() % 10_000_000));
    user.setVerified(true);
    user.setTermsAcceptedAt(Instant.now());
    return users.save(user);
  }

  private void createVehicle(int index) {
    VehicleModel vehicle = new VehicleModel();
    vehicle.setDriver(driver);
    vehicle.setPlate(String.format("PLT%02dX", index));
    vehicle.setBrand("Brand");
    vehicle.setModel("Model");
    vehicle.setManufactureYear(2020);
    vehicle.setColor("White");
    vehicle.setCapacity(12);
    vehicle.setPhotoFront(saveMedia());
    vehicle.setPhotoSide(saveMedia());
    vehicle.setPhotoDocument(saveMedia());
    vehicles.save(vehicle);
  }

  private void createDocument(int index) {
    DriverDocumentModel document = new DriverDocumentModel();
    document.setDriver(driver);
    document.setDocumentType(DocumentTypeEnum.values()[index % DocumentTypeEnum.values().length]);
    document.setStatus(DocumentStatusEnum.PENDING);
    document.setFile(saveMedia());
    document.setReviewedBy(
        saveUser("reviewer" + index + "@vanep.com", UserType.ADMIN, "reviewer-" + index));
    documents.save(document);
  }

  private Long createClient(int index) {
    ClientModel other = new ClientModel();
    other.setUser(saveUser("other" + index + "@vanep.com", UserType.CLIENT, "other-" + index));
    return clients.save(other).getId();
  }

  private void createDocumentForNewDriver(int index) {
    DriverModel owner = new DriverModel();
    owner.setUser(saveUser("owner" + index + "@vanep.com", UserType.DRIVER, "owner-" + index));
    owner.setBasePrice(new BigDecimal("80.00"));
    owner = drivers.save(owner);
    DriverDocumentModel document = new DriverDocumentModel();
    document.setDriver(owner);
    document.setDocumentType(DocumentTypeEnum.values()[0]);
    document.setStatus(DocumentStatusEnum.PENDING);
    document.setFile(saveMedia());
    documents.save(document);
  }

  private void createCnh(int index) {
    DriverCnhModel cnh = new DriverCnhModel();
    cnh.setDriver(driver);
    cnh.setRegistrationNumber("0000000000" + index);
    cnh.setCategory("B");
    cnh.setIssueDate(LocalDate.now().minusYears(1));
    cnh.setValidUntil(LocalDate.now().plusYears(2));
    cnhs.save(cnh);
  }

  private void createAssistant(int index) {
    UserModel assistantUser =
        saveUser("assistant" + index + "@vanep.com", UserType.ASSISTANT, "assistant-" + index);
    AssistantModel assistant = new AssistantModel();
    assistant.setDriver(driver);
    assistant.setUser(assistantUser);
    assistant.setPhoto(saveMedia());
    assistants.save(assistant);
  }

  private void createContract(int index) {
    createContractWithStatus(ContractStatus.AWAITING_SIGNATURES, index);
  }

  private void createContractWithStatus(ContractStatus status) {
    createContractWithStatus(status, 0);
  }

  private void createContractWithStatus(ContractStatus status, int dayOffset) {
    LocalDate startsOn = LocalDate.now().plusDays(dayOffset);
    ContractModel contract = new ContractModel();
    contract.setClientDriver(link);
    contract.setStatus(status);
    contract.setStartsOn(startsOn);
    contract.setEndsOn(startsOn.plusMonths(10));
    contract.setTotalAmount(new BigDecimal("4200.00"));
    contract.setInstallments(12);
    contract.setDueDay(5);
    contracts.save(contract);
  }

  private static void touchVehicles(List<VehicleModel> rows) {
    rows.forEach(
        vehicle -> {
          vehicle.getDriver().getUser().getEmail();
          if (vehicle.getPhotoFront() != null) {
            vehicle.getPhotoFront().getToken();
          }
          if (vehicle.getPhotoSide() != null) {
            vehicle.getPhotoSide().getToken();
          }
          if (vehicle.getPhotoDocument() != null) {
            vehicle.getPhotoDocument().getToken();
          }
        });
  }

  private static void touchDocuments(List<DriverDocumentModel> rows) {
    rows.forEach(
        document -> {
          document.getDriver().getUser().getEmail();
          if (document.getFile() != null) {
            document.getFile().getToken();
          }
          if (document.getReviewedBy() != null) {
            document.getReviewedBy().getEmail();
          }
        });
  }

  private static void touchCnhs(List<DriverCnhModel> rows) {
    rows.forEach(cnh -> cnh.getDriver().getUser().getEmail());
  }

  private static void touchAssistants(List<AssistantModel> rows) {
    rows.forEach(
        assistant -> {
          assistant.getUser().getEmail();
          assistant.getDriver().getUser().getEmail();
          if (assistant.getPhoto() != null) {
            assistant.getPhoto().getToken();
          }
        });
  }

  private static void touchContracts(List<ContractModel> rows) {
    rows.forEach(
        contract -> {
          contract.getClientDriver().getClient().getUser().getEmail();
          contract.getClientDriver().getDriver().getUser().getEmail();
        });
  }
}
