package br.com.vanep.clientday.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.clientdriver.enums.LinkOrigin;
import br.com.vanep.clientdriver.model.ClientDriverModel;
import br.com.vanep.clientdriver.repository.ClientDriverRepository;
import br.com.vanep.contract.enums.ContractStatus;
import br.com.vanep.contract.model.ContractItemModel;
import br.com.vanep.contract.model.ContractModel;
import br.com.vanep.contract.repository.ContractRepository;
import br.com.vanep.country.model.CountryModel;
import br.com.vanep.country.repository.CountryRepository;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.dependent.repository.DependentRepository;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.schedule.model.ScheduleModel;
import br.com.vanep.schedule.model.ScheduleSlotModel;
import br.com.vanep.school.model.SchoolModel;
import br.com.vanep.school.repository.SchoolRepository;
import br.com.vanep.shared.enums.OperationShift;
import br.com.vanep.shared.enums.RouteLeg;
import br.com.vanep.state.model.StateModel;
import br.com.vanep.state.repository.StateRepository;
import br.com.vanep.trip.service.TripService;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import br.com.vanep.vehicle.model.VehicleModel;
import br.com.vanep.vehicle.repository.VehicleRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
class ClientDayControllerTest {

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private ClientRepository clients;
  @Autowired private DriverRepository drivers;
  @Autowired private ClientDriverRepository links;
  @Autowired private ContractRepository contracts;
  @Autowired private DependentRepository dependents;
  @Autowired private SchoolRepository schools;
  @Autowired private VehicleRepository vehicles;
  @Autowired private CityRepository cities;
  @Autowired private StateRepository states;
  @Autowired private CountryRepository countries;

  private MockMvc mockMvc;
  private CityModel city;
  private SchoolModel school;
  private ClientModel client;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    city = createCity();
    school = createSchool();
    client = createClient();
  }

  @Test
  void showsTheVanTheChildRidesToday() throws Exception {
    DriverModel driver = createDriver();
    saveVehicle(driver);
    DependentModel ana = createDependent("Ana");
    ClientDriverModel link = createLink(driver);
    contracts.save(activeContract(link, everyDayItem(ana)));
    createDependent("Davi");

    mockMvc
        .perform(get("/api/clients/me/day").with(clientJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.date").value(LocalDate.now(TripService.SERVICE_ZONE).toString()))
        .andExpect(jsonPath("$.vans.length()").value(1))
        .andExpect(jsonPath("$.vans[0].driverName").value("Carlos Souza"))
        .andExpect(jsonPath("$.vans[0].vehicleModel").value("Sprinter"))
        .andExpect(jsonPath("$.vans[0].plate").value("SDF4E21"))
        .andExpect(jsonPath("$.vans[0].shift").value("MORNING"))
        .andExpect(jsonPath("$.vans[0].riders[0].name").value("Ana"))
        .andExpect(jsonPath("$.vans[0].boardingStart").value("06:40:00"))
        .andExpect(jsonPath("$.vans[0].schoolDeparture").value("12:30:00"))
        .andExpect(jsonPath("$.upcoming.length()").value(0))
        .andExpect(jsonPath("$.childrenWithoutVan.length()").value(1))
        .andExpect(jsonPath("$.childrenWithoutVan[0].name").value("Davi"));
  }

  @Test
  void ignoresContractsThatAreNotActive() throws Exception {
    DriverModel driver = createDriver();
    ClientDriverModel link = createLink(driver);
    ContractModel contract = activeContract(link, everyDayItem(createDependent("Ana")));
    contract.setStatus(ContractStatus.AWAITING_SIGNATURES);
    contracts.save(contract);

    mockMvc
        .perform(get("/api/clients/me/day").with(clientJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.vans.length()").value(0));
  }

  @Test
  void listsTheNextDaysWhenNothingRidesToday() throws Exception {
    DriverModel driver = createDriver();
    ClientDriverModel link = createLink(driver);
    DependentModel ana = createDependent("Ana");
    ContractItemModel item = everyDayItem(ana);
    DayOfWeek today = LocalDate.now(TripService.SERVICE_ZONE).getDayOfWeek();
    item.getSchedule().getSlots().removeIf(slot -> slot.getWeekday() == today);
    contracts.save(activeContract(link, item));

    mockMvc
        .perform(get("/api/clients/me/day").with(clientJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.vans.length()").value(0))
        .andExpect(jsonPath("$.upcoming.length()").value(5))
        .andExpect(jsonPath("$.upcoming[0].going").exists());
  }

  @Test
  void requiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/clients/me/day")).andExpect(status().isUnauthorized());
  }

  private JwtRequestPostProcessor clientJwt() {
    UserModel user = client.getUser();
    return jwt()
        .jwt(
            t ->
                t.claim("uid", user.getToken())
                    .claim("roles", List.of("ROLE_CLIENT"))
                    .subject(user.getEmail()))
        .authorities(new SimpleGrantedAuthority("ROLE_CLIENT"));
  }

  private ContractModel activeContract(ClientDriverModel link, ContractItemModel item) {
    LocalDate today = LocalDate.now(TripService.SERVICE_ZONE);
    ContractModel contract = new ContractModel();
    contract.setClientDriver(link);
    contract.setStatus(ContractStatus.ACTIVE);
    contract.setStartsOn(today.minusMonths(1));
    contract.setEndsOn(today.plusMonths(1));
    contract.setTotalAmount(new BigDecimal("4200.00"));
    contract.setInstallments(12);
    contract.setDueDay(5);
    contract.addItem(item);
    return contract;
  }

  /** Morning outbound at 06:40 and return at 12:30, every day of the week. */
  private ContractItemModel everyDayItem(DependentModel dependent) {
    ScheduleModel schedule = new ScheduleModel();
    for (DayOfWeek weekday : DayOfWeek.values()) {
      schedule.addSlot(slot(weekday, RouteLeg.OUTBOUND, LocalTime.of(6, 40)));
      schedule.addSlot(slot(weekday, RouteLeg.RETURN, LocalTime.of(12, 30)));
    }
    ContractItemModel item = new ContractItemModel();
    item.setDependent(dependent);
    item.setSchool(school);
    item.setPickupCity(city);
    item.setPickupZipCode("13015904");
    item.setPickupStreet("Rua A");
    item.setPickupNumber("10");
    item.setSchedule(schedule);
    return item;
  }

  private static ScheduleSlotModel slot(DayOfWeek weekday, RouteLeg leg, LocalTime pickup) {
    ScheduleSlotModel slot = new ScheduleSlotModel();
    slot.setWeekday(weekday);
    slot.setLeg(leg);
    slot.setShift(OperationShift.MORNING);
    slot.setPickupTime(pickup);
    return slot;
  }

  private ClientModel createClient() {
    ClientModel model = new ClientModel();
    model.setUser(createUser(UserType.CLIENT, "Responsável", "client@vanep.com", "10000000001"));
    return clients.save(model);
  }

  private DriverModel createDriver() {
    DriverModel driver = new DriverModel();
    driver.setUser(createUser(UserType.DRIVER, "Carlos Souza", "driver@vanep.com", "11144477735"));
    driver.setBasePrice(new BigDecimal("100.00"));
    return drivers.save(driver);
  }

  private ClientDriverModel createLink(DriverModel driver) {
    ClientDriverModel link = new ClientDriverModel();
    link.setClient(client);
    link.setDriver(driver);
    link.setOrigin(LinkOrigin.CLIENT_SEARCH);
    return links.save(link);
  }

  private DependentModel createDependent(String name) {
    DependentModel dependent = new DependentModel();
    dependent.setClientId(client.getId());
    dependent.setName(name);
    return dependents.save(dependent);
  }

  private void saveVehicle(DriverModel owner) {
    VehicleModel vehicle = new VehicleModel();
    vehicle.setDriver(owner);
    vehicle.setPlate("SDF4E21");
    vehicle.setBrand("Mercedes");
    vehicle.setModel("Sprinter");
    vehicle.setManufactureYear(2020);
    vehicle.setColor("Branca");
    vehicle.setCapacity(15);
    vehicles.save(vehicle);
  }

  private UserModel createUser(UserType type, String name, String email, String document) {
    UserModel user = new UserModel();
    user.setType(type);
    user.setName(name);
    user.setEmail(email);
    user.setDocument(document);
    user.setVerified(true);
    user.setTermsAcceptedAt(Instant.now());
    return users.save(user);
  }

  private SchoolModel createSchool() {
    SchoolModel model = new SchoolModel();
    model.setName("Escola Municipal");
    model.setCity(city);
    return schools.save(model);
  }

  private CityModel createCity() {
    CountryModel country = new CountryModel();
    country.setName("Brasil");
    country.setIsoCode("BR");
    country.setPhoneCode("+55");
    country.setCurrency("BRL");
    country.setLocale("pt-BR");
    country = countries.save(country);

    StateModel state = new StateModel();
    state.setName("São Paulo");
    state.setUf("SP");
    state.setCountry(country);
    state = states.save(state);

    CityModel model = new CityModel();
    model.setState(state);
    model.setName("Campinas");
    return cities.save(model);
  }
}
