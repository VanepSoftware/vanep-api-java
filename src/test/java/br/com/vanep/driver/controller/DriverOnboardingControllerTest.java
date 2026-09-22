package br.com.vanep.driver.controller;

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
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.drivercnh.model.DriverCnhModel;
import br.com.vanep.drivercnh.repository.DriverCnhRepository;
import br.com.vanep.driverdocument.enums.DocumentStatusEnum;
import br.com.vanep.driverdocument.enums.DocumentTypeEnum;
import br.com.vanep.driverdocument.model.DriverDocumentModel;
import br.com.vanep.driverdocument.repository.DriverDocumentRepository;
import br.com.vanep.state.model.StateModel;
import br.com.vanep.state.repository.StateRepository;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import br.com.vanep.vehicle.model.VehicleModel;
import br.com.vanep.vehicle.repository.VehicleRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
  @Autowired private AddressRepository addresses;
  @Autowired private CityRepository cities;
  @Autowired private StateRepository states;
  @Autowired private CountryRepository countries;

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
    vehicles.save(vehicle);

    DriverCnhModel cnh = new DriverCnhModel();
    cnh.setDriver(driver);
    cnh.setRegistrationNumber("12345678901");
    cnh.setCategory("D");
    cnh.setIssueDate(LocalDate.now().minusYears(2));
    cnh.setValidUntil(LocalDate.now().plusYears(3));
    cnh.setActive(true);
    driverCnhs.save(cnh);

    for (DocumentTypeEnum type :
        List.of(
            DocumentTypeEnum.CRLV,
            DocumentTypeEnum.VEHICLE_INSPECTION,
            DocumentTypeEnum.MUNICIPAL_AUTHORIZATION)) {
      DriverDocumentModel doc = new DriverDocumentModel();
      doc.setDriver(driver);
      doc.setDocumentType(type);
      doc.setStatus(DocumentStatusEnum.PENDING);
      doc.setActive(true);
      driverDocuments.save(doc);
    }
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
            jsonPath("$.documentsStep.missingTypes")
                .value(
                    org.hamcrest.Matchers.containsInAnyOrder(
                        "CRLV", "VEHICLE_INSPECTION", "MUNICIPAL_AUTHORIZATION")));
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
        .andExpect(jsonPath("$.documentsStep.missingTypes").isEmpty());
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
        .andExpect(status().isUnprocessableEntity());
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
        .andExpect(status().isBadRequest());
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
  void approveDriverWhenNotUnderReviewReturns400() throws Exception {
    driver.setApprovalStatus(DriverApprovalStatus.PENDING);
    driver = drivers.save(driver);

    mockMvc
        .perform(
            post("/api/drivers/" + driver.getToken() + "/approve").with(adminJwt(adminUserUid)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void rejectDriverForbiddenForRegularDriver() throws Exception {
    mockMvc
        .perform(
            post("/api/drivers/" + driver.getToken() + "/reject")
                .with(driverJwt(driverUserUid))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"Documentos pendentes\"}"))
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
                .content("{\"reason\": \"\"}"))
        .andExpect(status().isBadRequest());
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
                .content("{\"reason\": \"Foto da CNH ilegível\"}"))
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
                .content("{\"reason\": \"Foto ilegível\"}"))
        .andExpect(status().isBadRequest());
  }
}
