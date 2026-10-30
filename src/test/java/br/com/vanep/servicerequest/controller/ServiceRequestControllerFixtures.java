package br.com.vanep.servicerequest.controller;

import br.com.vanep.address.model.AddressModel;
import br.com.vanep.address.repository.AddressRepository;
import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.country.model.CountryModel;
import br.com.vanep.country.repository.CountryRepository;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.dependent.model.DependentSchoolDayModel;
import br.com.vanep.dependent.repository.DependentRepository;
import br.com.vanep.dependent.repository.DependentSchoolDayRepository;
import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.school.model.SchoolModel;
import br.com.vanep.school.repository.SchoolRepository;
import br.com.vanep.servicerequest.dto.ServiceRequestCreateRequestDTO;
import br.com.vanep.servicerequest.dto.ServiceRequestDayRequestDTO;
import br.com.vanep.servicerequest.dto.ServiceRequestItemRequestDTO;
import br.com.vanep.servicerequest.service.ServiceRequestService;
import br.com.vanep.shared.enums.SchoolShift;
import br.com.vanep.state.model.StateModel;
import br.com.vanep.state.repository.StateRepository;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.context.ApplicationContext;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

class ServiceRequestControllerFixtures {

  static final List<DayOfWeek> WEEKDAYS =
      List.of(
          DayOfWeek.MONDAY,
          DayOfWeek.TUESDAY,
          DayOfWeek.WEDNESDAY,
          DayOfWeek.THURSDAY,
          DayOfWeek.FRIDAY);

  private final UserRepository users;
  private final ClientRepository clients;
  private final DriverRepository drivers;
  private final DependentRepository dependents;
  private final DependentSchoolDayRepository schoolDays;
  private final SchoolRepository schools;
  private final AddressRepository addresses;
  private final CityRepository cities;
  private final StateRepository states;
  private final CountryRepository countries;
  private final ServiceRequestService service;

  final CityModel city;
  final SchoolModel school;

  ServiceRequestControllerFixtures(ApplicationContext context) {
    users = context.getBean(UserRepository.class);
    clients = context.getBean(ClientRepository.class);
    drivers = context.getBean(DriverRepository.class);
    dependents = context.getBean(DependentRepository.class);
    schoolDays = context.getBean(DependentSchoolDayRepository.class);
    schools = context.getBean(SchoolRepository.class);
    addresses = context.getBean(AddressRepository.class);
    cities = context.getBean(CityRepository.class);
    states = context.getBean(StateRepository.class);
    countries = context.getBean(CountryRepository.class);
    service = context.getBean(ServiceRequestService.class);
    city = createCity();
    school = createSchool();
  }

  static JwtRequestPostProcessor as(UserModel user) {
    return SecurityMockMvcRequestPostProcessors.jwt()
        .jwt(token -> token.claim("uid", user.getToken()).subject(user.getEmail()));
  }

  ClientModel createReadyClient(String name, String email, String document) {
    UserModel user = createUser(UserType.CLIENT, name, email, document);
    user.setPhone("19999990000");
    user.setAddressId(createAddress("Rua da Cliente", "Centro").getId());
    ClientModel client = new ClientModel();
    client.setUser(users.save(user));
    return clients.save(client);
  }

  DriverModel createAvailableDriver(String name, String email, String document) {
    DriverModel driver = new DriverModel();
    driver.setUser(createUser(UserType.DRIVER, name, email, document));
    driver.setBasePrice(new BigDecimal("100.00"));
    driver.setApprovalStatus(DriverApprovalStatus.APPROVED);
    driver.setAvailable(true);
    return drivers.save(driver);
  }

  UserModel createAdmin(String email, String document) {
    return createUser(UserType.ADMIN, "Admin", email, document);
  }

  DependentModel createDependentGoingEveryWeekday(
      ClientModel client, String name, LocalDate birthDate) {
    return createDependent(client, name, birthDate, weekday -> SchoolShift.MORNING);
  }

  DependentModel createDependent(
      ClientModel client,
      String name,
      LocalDate birthDate,
      Function<DayOfWeek, SchoolShift> shiftOfWeekday) {
    DependentModel dependent = new DependentModel();
    dependent.setClientId(client.getId());
    dependent.setName(name);
    dependent.setBirthDate(birthDate);
    dependent.setSchoolId(school.getId());
    dependent.setAddressId(createAddress("Rua A", "Cambuí").getId());
    dependent = dependents.save(dependent);
    for (DayOfWeek weekday : WEEKDAYS) {
      DependentSchoolDayModel day = new DependentSchoolDayModel();
      day.setDependentId(dependent.getId());
      day.setWeekday(weekday);
      day.setSchoolShift(shiftOfWeekday.apply(weekday));
      schoolDays.save(day);
    }
    return dependent;
  }

  String sendRequest(ClientModel client, DriverModel driver, DependentModel... chosen) {
    List<ServiceRequestItemRequestDTO> items =
        List.of(chosen).stream()
            .map(dependent -> new ServiceRequestItemRequestDTO(dependent.getToken(), roundTrips()))
            .toList();
    return service
        .send(
            client.getUser().getToken(),
            new ServiceRequestCreateRequestDTO(
                driver.getToken(), LocalDate.now().plusDays(7), "Portão azul", items))
        .token();
  }

  static Map<String, Object> createBody(String driverToken, List<Map<String, Object>> items) {
    return Map.of(
        "driverToken",
        driverToken,
        "desiredStartDate",
        LocalDate.now().plusDays(7).toString(),
        "notes",
        "Portão azul",
        "items",
        items);
  }

  static Map<String, Object> itemBody(String dependentToken) {
    return Map.of(
        "dependentToken",
        dependentToken,
        "days",
        WEEKDAYS.stream().map(weekday -> dayBody(weekday.name(), true, true)).toList());
  }

  static Map<String, Object> dayBody(String weekday, boolean needsOutbound, boolean needsReturn) {
    return Map.of("weekday", weekday, "needsOutbound", needsOutbound, "needsReturn", needsReturn);
  }

  private List<ServiceRequestDayRequestDTO> roundTrips() {
    return WEEKDAYS.stream()
        .map(weekday -> new ServiceRequestDayRequestDTO(weekday, true, true))
        .toList();
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

  private AddressModel createAddress(String street, String neighborhood) {
    AddressModel address = new AddressModel();
    address.setCity(city);
    address.setZipCode("13015904");
    address.setStreet(street);
    address.setNumber("10");
    address.setNeighborhood(neighborhood);
    return addresses.save(address);
  }

  private SchoolModel createSchool() {
    SchoolModel created = new SchoolModel();
    created.setName("Escola Municipal");
    created.setCity(city);
    return schools.save(created);
  }

  private CityModel createCity() {
    CountryModel country = new CountryModel();
    country.setName("Brasil");
    country.setIsoCode("BR");
    country.setPhoneCode("+55");
    country.setCurrency("BRL");
    country.setLocale("pt-BR");
    StateModel state = new StateModel();
    state.setName("São Paulo");
    state.setUf("SP");
    state.setCountry(countries.save(country));
    CityModel created = new CityModel();
    created.setState(states.save(state));
    created.setName("Campinas");
    return cities.save(created);
  }
}
