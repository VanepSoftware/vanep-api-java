package br.com.vanep.driver.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.address.model.AddressModel;
import br.com.vanep.address.repository.AddressRepository;
import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.country.model.CountryModel;
import br.com.vanep.country.repository.CountryRepository;
import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.enums.DriverRejectionReason;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.drivercnh.model.DriverCnhModel;
import br.com.vanep.drivercnh.repository.DriverCnhRepository;
import br.com.vanep.driverdocument.enums.DriverDocumentType;
import br.com.vanep.driverdocument.model.DriverDocumentModel;
import br.com.vanep.driverdocument.repository.DriverDocumentRepository;
import br.com.vanep.media.model.MediaFileModel;
import br.com.vanep.media.repository.MediaFileRepository;
import br.com.vanep.schooltransportcredential.service.SchoolTransportCredentialService;
import br.com.vanep.shared.document.enums.DocumentStatus;
import br.com.vanep.state.model.StateModel;
import br.com.vanep.state.repository.StateRepository;
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
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class DriverOnboardingControllerTest {

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private DriverRepository drivers;
  @Autowired private VehicleRepository vehicles;
  @Autowired private DriverCnhRepository driverCnhs;
  @Autowired private DriverDocumentRepository driverDocuments;
  @Autowired private VehicleDocumentRepository vehicleDocuments;
  @Autowired private MediaFileRepository mediaFiles;
  @Autowired private AddressRepository addresses;
  @Autowired private CityRepository cities;
  @Autowired private StateRepository states;
  @Autowired private CountryRepository countries;
  @Autowired private MessageSource messages;

  private MockMvc mockMvc;

  private UserModel driverUser;
  private DriverModel driver;
  private String driverUserUid;
  private String clientUserUid;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

    driverUser = new UserModel();
    driverUser.setType(UserType.DRIVER);
    driverUser.setName("Carlos Motorista");
    driverUser.setEmail("carlos@vanep.com");
    driverUser.setDocument("11122233344");
    driverUser.setVerified(true);
    driverUser.setTermsAcceptedAt(Instant.now());
    driverUser = users.save(driverUser);
    driverUserUid = driverUser.getToken();

    driver = new DriverModel();
    driver.setUser(driverUser);
    driver.setBasePrice(new BigDecimal("120.00"));
    driver.setApprovalStatus(DriverApprovalStatus.PENDING);
    driver = drivers.save(driver);

    UserModel clientUser = new UserModel();
    clientUser.setType(UserType.CLIENT);
    clientUser.setName("Joao Cliente");
    clientUser.setEmail("joao@vanep.com");
    clientUser.setDocument("55566677788");
    clientUser.setVerified(true);
    clientUser.setTermsAcceptedAt(Instant.now());
    clientUser = users.save(clientUser);
    clientUserUid = clientUser.getToken();

    UserModel adminUser = new UserModel();
    adminUser.setType(UserType.ADMIN);
    adminUser.setName("Admin");
    adminUser.setEmail("admin@vanep.com");
    adminUser.setDocument("99988877766");
    adminUser.setVerified(true);
    adminUser.setTermsAcceptedAt(Instant.now());
    adminUser = users.save(adminUser);
    adminUserUid = adminUser.getToken();
  }

  private String adminUserUid;

  private JwtRequestPostProcessor adminJwt(String uid) {
    return jwt()
        .jwt(t -> t.claim("uid", uid).claim("roles", List.of("ROLE_ADMIN")))
        .authorities(
            new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("approve_driver"));
  }

  private JwtRequestPostProcessor adminRoleOnlyJwt(String uid) {
    return jwt()
        .jwt(t -> t.claim("uid", uid).claim("roles", List.of("ROLE_ADMIN")))
        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
  }

  private JwtRequestPostProcessor driverJwt(String uid) {
    return jwt()
        .jwt(t -> t.claim("uid", uid).claim("roles", List.of("ROLE_DRIVER")))
        .authorities(new SimpleGrantedAuthority("ROLE_DRIVER"));
  }

  private JwtRequestPostProcessor clientJwt(String uid) {
    return jwt()
        .jwt(t -> t.claim("uid", uid).claim("roles", List.of("ROLE_CLIENT")))
        .authorities(new SimpleGrantedAuthority("ROLE_CLIENT"));
  }

  private void completeAllRequirements() {
    CountryModel country = new CountryModel();
    country.setName("Brasil");
    country.setIsoCode("BR");
    country.setPhoneCode("+55");
    country.setCurrency("BRL");
    country.setLocale("pt_BR");
    country = countries.save(country);

    StateModel state = new StateModel();
    state.setName("São Paulo");
    state.setUf("SP");
    state.setCountry(country);
    state = states.save(state);

    CityModel city = new CityModel();
    city.setName("Campinas");
    city.setState(state);
    city = cities.save(city);

    AddressModel address = new AddressModel();
    address.setCity(city);
    address.setStreet("Rua das Flores");
    address = addresses.save(address);

    driverUser.setAddressId(address.getId());
    driverUser = users.save(driverUser);

    VehicleModel vehicle = new VehicleModel();
    vehicle.setDriver(driver);
    vehicle.setPlate("ABC1D23");
    vehicle.setBrand("Mercedes-Benz");
    vehicle.setModel("Sprinter");
    vehicle.setManufactureYear(2022);
    vehicle.setColor("Branca");
    vehicle.setCapacity(18);
    vehicle.setActive(true);
    vehicle = vehicles.save(vehicle);

    DriverCnhModel cnh = new DriverCnhModel();
    cnh.setDriver(driver);
    cnh.setRegistrationNumber("12345678901");
    cnh.setCategory("D");
    cnh.setIssueDate(SchoolTransportCredentialService.today().minusYears(2));
    cnh.setValidUntil(SchoolTransportCredentialService.today().plusYears(3));
    cnh.setActive(true);
    cnh.getReview().setStatus(DocumentStatus.APPROVED);
    cnh.setFile(saveFile("cnh"));
    driverCnhs.save(cnh);

    for (DriverDocumentType type :
        List.of(DriverDocumentType.SCHOOL_DRIVER_COURSE, DriverDocumentType.CRIMINAL_RECORD)) {
      DriverDocumentModel document = new DriverDocumentModel();
      document.setDriver(driver);
      document.setDocumentType(type);
      document.setFile(saveFile(type.name().toLowerCase()));
      driverDocuments.save(document);
    }

    for (VehicleDocumentType type : VehicleDocumentType.values()) {
      VehicleDocumentModel doc = new VehicleDocumentModel();
      doc.setVehicle(vehicle);
      doc.setDocumentType(type);
      doc.setDocumentNumber(
          type == VehicleDocumentType.SCHOOL_VEHICLE_AUTHORIZATION ? "AUT-1" : null);
      doc.setExpiresAt(SchoolTransportCredentialService.today().plusMonths(6));
      doc.setFile(saveFile(type.name().toLowerCase()));
      vehicleDocuments.save(doc);
    }
  }

  private void approveEveryDocument() {
    driverDocuments
        .findAll()
        .forEach(
            document -> {
              document.getReview().setStatus(DocumentStatus.APPROVED);
              driverDocuments.save(document);
            });
    vehicleDocuments
        .findAll()
        .forEach(
            document -> {
              document.getReview().setStatus(DocumentStatus.APPROVED);
              vehicleDocuments.save(document);
            });
  }

  private MediaFileModel saveFile(String name) {
    MediaFileModel file = new MediaFileModel();
    file.setObjectKey("documents/" + name + "-" + System.nanoTime() + ".pdf");
    file.setMimeType("application/pdf");
    file.setSizeBytes(2048L);
    file.setOriginalName("doc.pdf");
    return mediaFiles.save(file);
  }

  @Test
  void getOnboardingStatusUnauthenticatedReturns401() throws Exception {
    mockMvc.perform(get("/api/drivers/me/onboarding")).andExpect(status().isUnauthorized());
  }

  @Test
  void getOnboardingStatusForbiddenForClient() throws Exception {
    mockMvc
        .perform(get("/api/drivers/me/onboarding").with(clientJwt(clientUserUid)))
        .andExpect(status().isForbidden());
  }

  @Test
  void getOnboardingStatusIncompleteReturns200WithPendingChecks() throws Exception {
    mockMvc
        .perform(get("/api/drivers/me/onboarding").with(driverJwt(driverUserUid)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.approvalStatus").value("PENDING"))
        .andExpect(jsonPath("$.canSubmit").value(false))
        .andExpect(jsonPath("$.profileStep.completed").value(false))
        .andExpect(jsonPath("$.vehicleStep.completed").value(false))
        .andExpect(jsonPath("$.cnhStep.completed").value(false))
        .andExpect(jsonPath("$.documentsStep.completed").value(false))
        .andExpect(
            jsonPath("$.documentsStep.missingDriverTypes")
                .value(org.hamcrest.Matchers.contains("SCHOOL_DRIVER_COURSE", "CRIMINAL_RECORD")))
        .andExpect(jsonPath("$.documentsStep.vehicles").isEmpty());
  }

  @Test
  void getOnboardingStatusCompleteReturnsCanSubmitTrue() throws Exception {
    completeAllRequirements();

    mockMvc
        .perform(get("/api/drivers/me/onboarding").with(driverJwt(driverUserUid)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.approvalStatus").value("PENDING"))
        .andExpect(jsonPath("$.canSubmit").value(true))
        .andExpect(jsonPath("$.profileStep.completed").value(true))
        .andExpect(jsonPath("$.vehicleStep.completed").value(true))
        .andExpect(jsonPath("$.cnhStep.completed").value(true))
        .andExpect(jsonPath("$.documentsStep.completed").value(true))
        .andExpect(jsonPath("$.documentsStep.missingDriverTypes").isEmpty())
        .andExpect(jsonPath("$.documentsStep.vehicles[0].plate").value("ABC1D23"))
        .andExpect(jsonPath("$.documentsStep.vehicles[0].completed").value(true))
        .andExpect(jsonPath("$.documentsStep.vehicles[0].missingTypes").isEmpty());
  }

  @Test
  void submitOnboardingUnauthenticatedReturns401() throws Exception {
    mockMvc.perform(post("/api/drivers/me/submit-onboarding")).andExpect(status().isUnauthorized());
  }

  @Test
  void submitOnboardingIncompleteReturns422() throws Exception {
    mockMvc
        .perform(
            post("/api/drivers/me/submit-onboarding")
                .with(driverJwt(driverUserUid))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("driver.onboarding.incomplete"))
        .andExpect(jsonPath("$.missingSteps").isArray());
  }

  @Test
  void submitOnboardingCompleteSuccessTransitionsToUnderReview() throws Exception {
    completeAllRequirements();

    mockMvc
        .perform(
            post("/api/drivers/me/submit-onboarding")
                .with(driverJwt(driverUserUid))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.approvalStatus").value("UNDER_REVIEW"))
        .andExpect(jsonPath("$.canSubmit").value(false))
        .andExpect(jsonPath("$.submittedAt").isNotEmpty())
        .andExpect(jsonPath("$.rejectionReason").doesNotExist());
  }

  @Test
  void submitOnboardingWhenAlreadyUnderReviewReturns400() throws Exception {
    driver.setApprovalStatus(DriverApprovalStatus.UNDER_REVIEW);
    driver.setSubmittedAt(Instant.now());
    drivers.save(driver);

    mockMvc
        .perform(
            post("/api/drivers/me/submit-onboarding")
                .with(driverJwt(driverUserUid))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("driver.onboarding.already_submitted"));
  }

  @Test
  void approveDriverForbiddenForRegularDriverOrClient() throws Exception {
    mockMvc
        .perform(
            post("/api/drivers/" + driver.getToken() + "/approve").with(driverJwt(driverUserUid)))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            post("/api/drivers/" + driver.getToken() + "/approve").with(clientJwt(clientUserUid)))
        .andExpect(status().isForbidden());
  }

  @Test
  void approveDriverSuccessForAdmin() throws Exception {
    completeAllRequirements();
    approveEveryDocument();
    driver.setApprovalStatus(DriverApprovalStatus.UNDER_REVIEW);
    driver.setActive(false);
    driver = drivers.save(driver);

    mockMvc
        .perform(
            post("/api/drivers/" + driver.getToken() + "/approve").with(adminJwt(adminUserUid)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.approvalStatus").value("APPROVED"))
        .andExpect(jsonPath("$.active").value(true));
  }

  @Test
  void approveDriverSuccessForAdminWithRoleAdminOnly() throws Exception {
    completeAllRequirements();
    approveEveryDocument();
    driver.setApprovalStatus(DriverApprovalStatus.UNDER_REVIEW);
    driver.setActive(false);
    driver = drivers.save(driver);

    mockMvc
        .perform(
            post("/api/drivers/" + driver.getToken() + "/approve")
                .with(adminRoleOnlyJwt(adminUserUid)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.approvalStatus").value("APPROVED"))
        .andExpect(jsonPath("$.active").value(true));
  }

  @Test
  void approvingWithThePendingInspectionOfTheOnlyVanIsRefused() throws Exception {
    completeAllRequirements();
    approveEveryDocument();
    VehicleDocumentModel inspection =
        vehicleDocuments.findAll().stream()
            .filter(
                document -> document.getDocumentType() == VehicleDocumentType.VEHICLE_INSPECTION)
            .findFirst()
            .orElseThrow();
    inspection.getReview().setStatus(DocumentStatus.PENDING);
    vehicleDocuments.save(inspection);
    driver.setApprovalStatus(DriverApprovalStatus.UNDER_REVIEW);
    driver = drivers.save(driver);

    mockMvc
        .perform(
            post("/api/drivers/" + driver.getToken() + "/approve").with(adminJwt(adminUserUid)))
        .andExpect(status().isUnprocessableContent())
        .andExpect(
            jsonPath("$.detail")
                .value(
                    messages.getMessage(
                        "driver.onboarding.documents_not_approved",
                        null,
                        Locale.forLanguageTag("pt-BR"))))
        .andExpect(jsonPath("$.code").value("driver.onboarding.documents_not_approved"))
        .andExpect(jsonPath("$.blockers.length()").value(1))
        .andExpect(jsonPath("$.blockers[0].kind").value("VEHICLE_REQUIREMENT"))
        .andExpect(jsonPath("$.blockers[0].requirement").value("VEHICLE_INSPECTION"))
        .andExpect(jsonPath("$.blockers[0].state").value("PENDING"))
        .andExpect(jsonPath("$.blockers[0].plate").value("ABC1D23"));

    assertThat(drivers.findByToken(driver.getToken()).orElseThrow().getApprovalStatus())
        .isEqualTo(DriverApprovalStatus.UNDER_REVIEW);
  }

  @Test
  void approveDriverWhenNotUnderReviewReturns400() throws Exception {
    driver.setApprovalStatus(DriverApprovalStatus.PENDING);
    driver = drivers.save(driver);

    mockMvc
        .perform(
            post("/api/drivers/" + driver.getToken() + "/approve").with(adminJwt(adminUserUid)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("driver.onboarding.not_under_review"));
  }

  @Test
  void rejectDriverForbiddenForRegularDriver() throws Exception {
    mockMvc
        .perform(
            post("/api/drivers/" + driver.getToken() + "/reject")
                .with(driverJwt(driverUserUid))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reasonCode\": \"OTHER\", \"reason\": \"Documentos pendentes\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void rejectDriverValidationFailsWhenReasonBlank() throws Exception {
    driver.setApprovalStatus(DriverApprovalStatus.UNDER_REVIEW);
    driver = drivers.save(driver);

    mockMvc
        .perform(
            post("/api/drivers/" + driver.getToken() + "/reject")
                .with(adminJwt(adminUserUid))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"Sem código\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.field").value("reasonCode"));

    mockMvc
        .perform(
            post("/api/drivers/" + driver.getToken() + "/reject")
                .with(adminJwt(adminUserUid))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reasonCode\": \"OTHER\", \"reason\": \" \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("driver.rejection.reason.required"));

    mockMvc
        .perform(
            post("/api/drivers/" + driver.getToken() + "/reject")
                .with(adminJwt(adminUserUid))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reasonCode\": \"DUPLICATE_ACCOUNT\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.approvalStatus").value("REJECTED"));
    assertThat(drivers.findByToken(driver.getToken()).orElseThrow().getRejectionReasonCode())
        .isEqualTo(DriverRejectionReason.DUPLICATE_ACCOUNT);
  }

  @Test
  void rejectDriverSuccessForAdmin() throws Exception {
    driver.setApprovalStatus(DriverApprovalStatus.UNDER_REVIEW);
    driver = drivers.save(driver);

    mockMvc
        .perform(
            post("/api/drivers/" + driver.getToken() + "/reject")
                .with(adminJwt(adminUserUid))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reasonCode\": \"OTHER\", \"reason\": \"Foto da CNH ilegível\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.approvalStatus").value("REJECTED"));
  }

  @Test
  void rejectDriverSuccessForAdminWithRoleAdminOnly() throws Exception {
    driver.setApprovalStatus(DriverApprovalStatus.UNDER_REVIEW);
    driver = drivers.save(driver);

    mockMvc
        .perform(
            post("/api/drivers/" + driver.getToken() + "/reject")
                .with(adminRoleOnlyJwt(adminUserUid))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reasonCode\": \"OTHER\", \"reason\": \"Foto da CNH ilegível\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.approvalStatus").value("REJECTED"));
  }

  @Test
  void rejectDriverWhenNotUnderReviewReturns400() throws Exception {
    driver.setApprovalStatus(DriverApprovalStatus.APPROVED);
    driver = drivers.save(driver);

    mockMvc
        .perform(
            post("/api/drivers/" + driver.getToken() + "/reject")
                .with(adminJwt(adminUserUid))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reasonCode\": \"OTHER\", \"reason\": \"Foto ilegível\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("driver.onboarding.not_under_review"));
  }

  @Test
  void anApprovedButExpiredDocumentIsMissingInTheOnboarding() throws Exception {
    completeAllRequirements();
    approveEveryDocument();
    DriverDocumentModel criminalRecord =
        driverDocuments.findAll().stream()
            .filter(document -> document.getDocumentType() == DriverDocumentType.CRIMINAL_RECORD)
            .findFirst()
            .orElseThrow();
    criminalRecord.setExpiresAt(SchoolTransportCredentialService.today().minusDays(1));
    driverDocuments.save(criminalRecord);

    mockMvc
        .perform(get("/api/drivers/me/onboarding").with(driverJwt(driverUserUid)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.documentsStep.completed").value(false))
        .andExpect(jsonPath("$.documentsStep.missingDriverTypes[0]").value("CRIMINAL_RECORD"));
  }

  @Test
  void listPendingDriversReturnsOnlyUnderReviewForAdmin() throws Exception {
    driver.setApprovalStatus(DriverApprovalStatus.UNDER_REVIEW);
    driver.setSubmittedAt(Instant.now());
    driver = drivers.save(driver);

    UserModel otherUser = new UserModel();
    otherUser.setType(UserType.DRIVER);
    otherUser.setName("Outro Motorista");
    otherUser.setEmail("outro@vanep.com");
    otherUser.setDocument("99911122233");
    otherUser.setVerified(true);
    otherUser.setTermsAcceptedAt(Instant.now());
    otherUser = users.save(otherUser);

    DriverModel approvedDriver = new DriverModel();
    approvedDriver.setUser(otherUser);
    approvedDriver.setBasePrice(new BigDecimal("100.00"));
    approvedDriver.setApprovalStatus(DriverApprovalStatus.APPROVED);
    drivers.save(approvedDriver);

    mockMvc
        .perform(get("/api/drivers/pending").with(adminJwt(adminUserUid)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].token").value(driver.getToken()))
        .andExpect(jsonPath("$.content[0].approvalStatus").value("UNDER_REVIEW"))
        .andExpect(jsonPath("$.content[0].name").value("Carlos Motorista"));
  }

  @Test
  void listPendingDriversAllowedForAdminRoleOnly() throws Exception {
    driver.setApprovalStatus(DriverApprovalStatus.UNDER_REVIEW);
    driver = drivers.save(driver);

    mockMvc
        .perform(get("/api/drivers/pending").with(adminRoleOnlyJwt(adminUserUid)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].token").value(driver.getToken()));
  }

  @Test
  void listPendingDriversForbiddenForDriver() throws Exception {
    mockMvc
        .perform(get("/api/drivers/pending").with(driverJwt(driverUserUid)))
        .andExpect(status().isForbidden());
  }

  @Test
  void listPendingDriversForbiddenForClient() throws Exception {
    mockMvc
        .perform(get("/api/drivers/pending").with(clientJwt(clientUserUid)))
        .andExpect(status().isForbidden());
  }

  @Test
  void listPendingDriversUnauthorizedForAnonymous() throws Exception {
    mockMvc.perform(get("/api/drivers/pending")).andExpect(status().isUnauthorized());
  }
}
