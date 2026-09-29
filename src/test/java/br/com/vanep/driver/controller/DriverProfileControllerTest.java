package br.com.vanep.driver.controller;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.country.model.CountryModel;
import br.com.vanep.country.repository.CountryRepository;
import br.com.vanep.district.model.DistrictModel;
import br.com.vanep.district.repository.DistrictRepository;
import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.driverservicearea.model.DriverServiceAreaModel;
import br.com.vanep.driverservicearea.repository.DriverServiceAreaRepository;
import br.com.vanep.state.repository.StateRepository;
import br.com.vanep.state.seed.StateSeeder;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import br.com.vanep.vehicle.model.VehicleModel;
import br.com.vanep.vehicle.repository.VehicleRepository;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
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
class DriverProfileControllerTest {

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private DriverRepository drivers;
  @Autowired private VehicleRepository vehicles;
  @Autowired private CountryRepository countries;
  @Autowired private StateSeeder stateSeeder;
  @Autowired private StateRepository states;
  @Autowired private CityRepository cities;
  @Autowired private DistrictRepository districts;
  @Autowired private DriverServiceAreaRepository areas;

  private MockMvc mockMvc;
  private String clientUid;
  private int documentSequence;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

    UserModel client = new UserModel();
    client.setType(UserType.CLIENT);
    client.setName("Cliente");
    client.setEmail("cliente@vanep.com");
    client.setDocument("10101010101");
    client.setVerified(true);
    client.setTermsAcceptedAt(Instant.now());
    clientUid = users.save(client).getToken();
  }

  @Test
  void rejectsUnauthenticated() throws Exception {
    DriverModel driver = saveDriver("carlos@vanep.com", DriverApprovalStatus.APPROVED);

    mockMvc
        .perform(get("/api/drivers/" + driver.getToken() + "/profile"))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/drivers/recommended")).andExpect(status().isUnauthorized());
  }

  @Test
  void aClientWithoutDriverPermissionsReadsTheProfile() throws Exception {
    DriverModel driver = saveDriver("carlos@vanep.com", DriverApprovalStatus.APPROVED);

    mockMvc
        .perform(get("/api/drivers/" + driver.getToken() + "/profile").with(clientJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(driver.getToken()))
        .andExpect(jsonPath("$.name").value("Motorista carlos@vanep.com"))
        .andExpect(jsonPath("$.phone").value("61999990000"))
        .andExpect(jsonPath("$.bio").value("Levo criança há 8 anos."))
        .andExpect(jsonPath("$.experienceYears").value(8))
        .andExpect(jsonPath("$.rating").value(4.8));
  }

  @Test
  void aDriverNeverRatedShowsNoRating() throws Exception {
    DriverModel driver = saveDriver("carlos@vanep.com", DriverApprovalStatus.APPROVED);
    driver.setRating(null);
    drivers.save(driver);

    mockMvc
        .perform(get("/api/drivers/" + driver.getToken() + "/profile").with(clientJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rating").value(nullValue()));
  }

  @Test
  void neverExposesPrivateDataOfTheDriver() throws Exception {
    DriverModel driver = saveDriver("carlos@vanep.com", DriverApprovalStatus.APPROVED);

    mockMvc
        .perform(get("/api/drivers/" + driver.getToken() + "/profile").with(clientJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").doesNotExist())
        .andExpect(jsonPath("$.document").doesNotExist())
        .andExpect(jsonPath("$.cnpj").doesNotExist())
        .andExpect(jsonPath("$.approvalStatus").doesNotExist())
        .andExpect(jsonPath("$.address").doesNotExist());
  }

  @Test
  void aDriverTheSearchWouldHideIsNotFound() throws Exception {
    DriverModel pending = saveDriver("pendente@vanep.com", DriverApprovalStatus.PENDING);
    DriverModel rejected = saveDriver("recusado@vanep.com", DriverApprovalStatus.REJECTED);
    DriverModel inactive = saveDriver("inativo@vanep.com", DriverApprovalStatus.APPROVED);
    inactive.setActive(false);
    drivers.save(inactive);

    for (DriverModel hidden : List.of(pending, rejected, inactive)) {
      mockMvc
          .perform(get("/api/drivers/" + hidden.getToken() + "/profile").with(clientJwt()))
          .andExpect(status().isNotFound());
    }
    mockMvc
        .perform(get("/api/drivers/nao-existe/profile").with(clientJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void listsTheRegionsTheDriverCovers() throws Exception {
    DriverModel driver = saveDriver("carlos@vanep.com", DriverApprovalStatus.APPROVED);
    CityModel brasilia = seedBrasilia();
    giveArea(driver, brasilia, saveDistrict(brasilia, "Taguatinga"));
    giveArea(driver, brasilia, saveDistrict(brasilia, "Águas Claras"));

    mockMvc
        .perform(get("/api/drivers/" + driver.getToken() + "/profile").with(clientJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.serviceAreas.length()").value(2))
        .andExpect(jsonPath("$.serviceAreas", hasItems("Taguatinga", "Águas Claras")));
  }

  @Test
  void showsTheActiveVansWithoutPlateOrDocumentPhoto() throws Exception {
    DriverModel driver = saveDriver("carlos@vanep.com", DriverApprovalStatus.APPROVED);
    VehicleModel sprinter = saveVehicle(driver, "ABC1D23", "Sprinter", true);
    saveVehicle(driver, "XYZ9Z99", "Transit", false);
    upload("/api/vehicles/" + sprinter.getToken() + "/photo-front", ownerJwt(driver));
    upload("/api/vehicles/" + sprinter.getToken() + "/photo-document", ownerJwt(driver));

    mockMvc
        .perform(get("/api/drivers/" + driver.getToken() + "/profile").with(clientJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.vehicles.length()").value(1))
        .andExpect(jsonPath("$.vehicles[0].token").value(sprinter.getToken()))
        .andExpect(jsonPath("$.vehicles[0].brand").value("Mercedes-Benz"))
        .andExpect(jsonPath("$.vehicles[0].model").value("Sprinter"))
        .andExpect(jsonPath("$.vehicles[0].manufactureYear").value(2021))
        .andExpect(jsonPath("$.vehicles[0].capacity").value(15))
        .andExpect(
            jsonPath("$.vehicles[0].photoFront")
                .value(startsWith("/api/vehicles/" + sprinter.getToken() + "/photo-front?v=")))
        .andExpect(jsonPath("$.vehicles[0].photoSide").doesNotExist())
        .andExpect(jsonPath("$.vehicles[0].photoDocument").doesNotExist())
        .andExpect(jsonPath("$.vehicles[0].plate").doesNotExist());
  }

  @Test
  void aClientSeesTheFrontAndSidePhotosOfAnApprovedDriversVan() throws Exception {
    DriverModel driver = saveDriver("carlos@vanep.com", DriverApprovalStatus.APPROVED);
    VehicleModel sprinter = saveVehicle(driver, "ABC1D23", "Sprinter", true);
    String base = "/api/vehicles/" + sprinter.getToken();
    upload(base + "/photo-front", ownerJwt(driver));
    upload(base + "/photo-side", ownerJwt(driver));
    upload(base + "/photo-document", ownerJwt(driver));

    mockMvc.perform(get(base + "/photo-front").with(clientJwt())).andExpect(status().isOk());
    mockMvc.perform(get(base + "/photo-side").with(clientJwt())).andExpect(status().isOk());
    mockMvc
        .perform(get(base + "/photo-document").with(clientJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void aClientDoesNotSeeTheVanPhotosOfADriverNotApprovedYet() throws Exception {
    DriverModel driver = saveDriver("pendente@vanep.com", DriverApprovalStatus.PENDING);
    VehicleModel sprinter = saveVehicle(driver, "ABC1D23", "Sprinter", true);
    upload("/api/vehicles/" + sprinter.getToken() + "/photo-front", ownerJwt(driver));

    mockMvc
        .perform(get("/api/vehicles/" + sprinter.getToken() + "/photo-front").with(clientJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void aClientSeesThePhotoOfAnApprovedDriverOnly() throws Exception {
    DriverModel approved = saveDriver("carlos@vanep.com", DriverApprovalStatus.APPROVED);
    DriverModel pending = saveDriver("pendente@vanep.com", DriverApprovalStatus.PENDING);
    upload("/api/drivers/" + approved.getToken() + "/photo", ownerJwt(approved));
    upload("/api/drivers/" + pending.getToken() + "/photo", ownerJwt(pending));

    mockMvc
        .perform(get("/api/drivers/" + approved.getToken() + "/photo").with(clientJwt()))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/drivers/" + pending.getToken() + "/photo").with(clientJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void aClientNoLongerReadsTheAdministrativeDriverRoutes() throws Exception {
    DriverModel driver = saveDriver("carlos@vanep.com", DriverApprovalStatus.APPROVED);

    mockMvc.perform(get("/api/drivers").with(clientJwt())).andExpect(status().isForbidden());
    mockMvc
        .perform(get("/api/drivers/" + driver.getToken()).with(clientJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void recommendsOnlyApprovedAndActiveDriversNewestFirst() throws Exception {
    saveDriver("antigo@vanep.com", DriverApprovalStatus.APPROVED);
    saveDriver("pendente@vanep.com", DriverApprovalStatus.PENDING);
    DriverModel inactive = saveDriver("inativo@vanep.com", DriverApprovalStatus.APPROVED);
    inactive.setActive(false);
    drivers.save(inactive);
    saveDriver("novo@vanep.com", DriverApprovalStatus.APPROVED);

    mockMvc
        .perform(get("/api/drivers/recommended").with(clientJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.content[0].name").value("Motorista novo@vanep.com"))
        .andExpect(jsonPath("$.content[1].name").value("Motorista antigo@vanep.com"))
        .andExpect(jsonPath("$.content[0].email").doesNotExist())
        .andExpect(jsonPath("$.content[0].document").doesNotExist())
        .andExpect(jsonPath("$.content[0].phone").doesNotExist());
  }

  @Test
  void recommendationIgnoresACallerChosenSort() throws Exception {
    saveDriver("antigo@vanep.com", DriverApprovalStatus.APPROVED);
    saveDriver("novo@vanep.com", DriverApprovalStatus.APPROVED);

    mockMvc
        .perform(get("/api/drivers/recommended").with(clientJwt()).param("sort", "createdAt,asc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].name").value("Motorista novo@vanep.com"));
  }

  @Test
  void recommendationCarriesTheRegionsAndPaginates() throws Exception {
    DriverModel antigo = saveDriver("antigo@vanep.com", DriverApprovalStatus.APPROVED);
    saveDriver("novo@vanep.com", DriverApprovalStatus.APPROVED);
    CityModel brasilia = seedBrasilia();
    giveArea(antigo, brasilia, saveDistrict(brasilia, "Ceilândia"));

    mockMvc
        .perform(
            get("/api/drivers/recommended").with(clientJwt()).param("size", "1").param("page", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].name").value("Motorista antigo@vanep.com"))
        .andExpect(jsonPath("$.content[0].serviceAreas[0]").value("Ceilândia"));
  }

  private DriverModel saveDriver(String email, DriverApprovalStatus approvalStatus) {
    UserModel user = new UserModel();
    user.setType(UserType.DRIVER);
    user.setName("Motorista " + email);
    user.setEmail(email);
    user.setPhone("61999990000");
    user.setDocument(String.format("%011d", ++documentSequence));
    user.setVerified(true);
    user.setTermsAcceptedAt(Instant.now());
    user = users.save(user);

    DriverModel driver = new DriverModel();
    driver.setUser(user);
    driver.setBasePrice(new BigDecimal("100.00"));
    driver.setBio("Levo criança há 8 anos.");
    driver.setExperienceYears(8);
    driver.setRating(new BigDecimal("4.80"));
    driver.setApprovalStatus(approvalStatus);
    return drivers.save(driver);
  }

  private VehicleModel saveVehicle(DriverModel driver, String plate, String model, boolean active) {
    VehicleModel vehicle = new VehicleModel();
    vehicle.setDriver(driver);
    vehicle.setPlate(plate);
    vehicle.setBrand("Mercedes-Benz");
    vehicle.setModel(model);
    vehicle.setManufactureYear(2021);
    vehicle.setColor("Branca");
    vehicle.setCapacity(15);
    vehicle.setActive(active);
    return vehicles.save(vehicle);
  }

  private CityModel seedBrasilia() {
    CountryModel brasil = new CountryModel();
    brasil.setName("Brasil");
    brasil.setIsoCode("BR");
    brasil.setPhoneCode("+55");
    brasil.setCurrency("BRL");
    countries.save(brasil);
    stateSeeder.seed();

    CityModel brasilia = new CityModel();
    brasilia.setName("Brasília");
    brasilia.setState(states.findAll().getFirst());
    return cities.save(brasilia);
  }

  private DistrictModel saveDistrict(CityModel city, String name) {
    DistrictModel district = new DistrictModel();
    district.setCity(city);
    district.setName(name);
    return districts.save(district);
  }

  private void giveArea(DriverModel driver, CityModel city, DistrictModel district) {
    DriverServiceAreaModel area = new DriverServiceAreaModel();
    area.setDriver(driver);
    area.setCity(city);
    area.setDistrict(district);
    areas.save(area);
  }

  private void upload(String path, JwtRequestPostProcessor who) throws Exception {
    mockMvc
        .perform(
            multipart(path)
                .file(new MockMultipartFile("file", "foto.png", "image/png", png()))
                .with(who))
        .andExpect(status().isCreated());
  }

  private static byte[] png() {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (int value : new int[] {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01, 0x02}) {
      out.write(value);
    }
    return out.toByteArray();
  }

  private JwtRequestPostProcessor clientJwt() {
    return jwt()
        .jwt(token -> token.claim("uid", clientUid).claim("roles", List.of("ROLE_CLIENT")))
        .authorities(new SimpleGrantedAuthority("ROLE_CLIENT"));
  }

  private JwtRequestPostProcessor ownerJwt(DriverModel driver) {
    String uid = driver.getUser().getToken();
    return jwt()
        .jwt(token -> token.claim("uid", uid).claim("roles", List.of("ROLE_DRIVER")))
        .authorities(new SimpleGrantedAuthority("ROLE_DRIVER"));
  }
}
