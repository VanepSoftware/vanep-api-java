package br.com.vanep.contractreadiness.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import br.com.vanep.school.model.SchoolModel;
import br.com.vanep.school.repository.SchoolRepository;
import br.com.vanep.shared.SqlStatementCounter;
import br.com.vanep.shared.enums.SchoolShift;
import br.com.vanep.state.model.StateModel;
import br.com.vanep.state.repository.StateRepository;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ContractReadinessControllerTest {
  private static final String PATH = "/api/clients/me/contract-readiness";

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private ClientRepository clients;
  @Autowired private DependentRepository dependents;
  @Autowired private DependentSchoolDayRepository schoolDays;
  @Autowired private SchoolRepository schools;
  @Autowired private AddressRepository addresses;
  @Autowired private CityRepository cities;
  @Autowired private StateRepository states;
  @Autowired private CountryRepository countries;
  @Autowired private EntityManagerFactory entityManagerFactory;

  private MockMvc mockMvc;
  private CityModel city;
  private SchoolModel school;
  private UserModel ana;
  private ClientModel anaClient;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    city = createCity();
    school = new SchoolModel();
    school.setName("Escola Azul");
    school = schools.save(school);
    ana = createUser(UserType.CLIENT, "ana@vanep.com", "11144477735");
    ana.setPhone("11999990000");
    ana.setAddressId(createAddress().getId());
    ana = users.save(ana);
    anaClient = createClient(ana);
  }

  @Test
  void aClientWithEverythingInPlaceIsReady() throws Exception {
    DependentModel lucas = createReadyDependent(anaClient, "Lucas");

    mockMvc
        .perform(get(PATH).param("dependentTokens", lucas.getToken()).with(as(ana)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ready").value(true))
        .andExpect(jsonPath("$.missing").isEmpty());
  }

  @Test
  void listsWhatIsMissingWithTheDependentTokenOnlyOnDependentEntries() throws Exception {
    ana.setPhone(null);
    users.save(ana);
    DependentModel lucas = createDependent(anaClient, "Lucas");

    mockMvc
        .perform(get(PATH).param("dependentTokens", lucas.getToken()).with(as(ana)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ready").value(false))
        .andExpect(jsonPath("$.missing.length()").value(5))
        .andExpect(jsonPath("$.missing[0].subject").value("CLIENT"))
        .andExpect(jsonPath("$.missing[0].field").value("PHONE"))
        .andExpect(jsonPath("$.missing[0].dependentToken").doesNotExist())
        .andExpect(jsonPath("$.missing[1].subject").value("DEPENDENT"))
        .andExpect(jsonPath("$.missing[1].dependentToken").value(lucas.getToken()))
        .andExpect(jsonPath("$.missing[1].field").value("BIRTH_DATE"))
        .andExpect(jsonPath("$.missing[4].field").value("SCHOOL_DAYS"));
  }

  @Test
  void acceptsTheTokensRepeatedOrSeparatedByCommas() throws Exception {
    DependentModel lucas = createDependent(anaClient, "Lucas");
    DependentModel marina = createReadyDependent(anaClient, "Marina");

    mockMvc
        .perform(
            get(PATH).param("dependentTokens", lucas.getToken(), marina.getToken()).with(as(ana)))
        .andExpect(jsonPath("$.missing.length()").value(4));
    mockMvc
        .perform(
            get(PATH)
                .param("dependentTokens", lucas.getToken() + "," + marina.getToken())
                .with(as(ana)))
        .andExpect(jsonPath("$.missing.length()").value(4));
  }

  @Test
  void withoutDependentTokensOnlyTheClientIsChecked() throws Exception {
    createDependent(anaClient, "Lucas");

    mockMvc
        .perform(get(PATH).with(as(ana)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ready").value(true));
  }

  @Test
  void aDependentOfAnotherClientIsNotFound() throws Exception {
    UserModel bruno = createUser(UserType.CLIENT, "bruno@vanep.com", "12345678909");
    DependentModel brunosKid = createReadyDependent(createClient(bruno), "Davi");

    mockMvc
        .perform(get(PATH).param("dependentTokens", brunosKid.getToken()).with(as(ana)))
        .andExpect(status().isNotFound());
  }

  @Test
  void aRemovedDependentIsNotFound() throws Exception {
    DependentModel lucas = createReadyDependent(anaClient, "Lucas");
    dependents.delete(lucas);

    mockMvc
        .perform(get(PATH).param("dependentTokens", lucas.getToken()).with(as(ana)))
        .andExpect(status().isNotFound());
  }

  @Test
  void onlyAClientCanAsk() throws Exception {
    UserModel gustavo = createUser(UserType.DRIVER, "gustavo@vanep.com", "52998224725");

    mockMvc.perform(get(PATH).with(as(gustavo))).andExpect(status().isForbidden());
  }

  @Test
  void requiresAuthentication() throws Exception {
    mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
  }

  @Test
  void readsOneOrThreeDependentsInTheSameNumberOfStatements() {
    SqlStatementCounter counter = new SqlStatementCounter(entityManagerFactory);
    DependentModel lucas = createReadyDependent(anaClient, "Lucas");
    long withOne = counter.countStatements(() -> askFor(lucas.getToken()));

    DependentModel marina = createReadyDependent(anaClient, "Marina");
    DependentModel teo = createReadyDependent(anaClient, "Teo");
    long withThree =
        counter.countStatements(
            () -> askFor(lucas.getToken() + "," + marina.getToken() + "," + teo.getToken()));

    assertThat(withThree).isEqualTo(withOne);
  }

  private void askFor(String tokens) {
    try {
      mockMvc
          .perform(get(PATH).param("dependentTokens", tokens).with(as(ana)))
          .andExpect(status().isOk());
    } catch (Exception failure) {
      throw new IllegalStateException(failure);
    }
  }

  private JwtRequestPostProcessor as(UserModel user) {
    return jwt().jwt(token -> token.claim("uid", user.getToken()).subject(user.getEmail()));
  }

  private DependentModel createReadyDependent(ClientModel client, String name) {
    DependentModel dependent = createDependent(client, name);
    dependent.setBirthDate(LocalDate.of(2016, 4, 2));
    dependent.setSchoolId(school.getId());
    dependent.setAddressId(createAddress().getId());
    dependent = dependents.save(dependent);
    DependentSchoolDayModel monday = new DependentSchoolDayModel();
    monday.setDependentId(dependent.getId());
    monday.setWeekday(DayOfWeek.MONDAY);
    monday.setSchoolShift(SchoolShift.MORNING);
    schoolDays.save(monday);
    return dependent;
  }

  private DependentModel createDependent(ClientModel client, String name) {
    DependentModel dependent = new DependentModel();
    dependent.setClientId(client.getId());
    dependent.setName(name);
    return dependents.save(dependent);
  }

  private UserModel createUser(UserType type, String email, String document) {
    UserModel user = new UserModel();
    user.setType(type);
    user.setName(email);
    user.setEmail(email);
    user.setDocument(document);
    user.setVerified(true);
    user.setTermsAcceptedAt(Instant.now());
    return users.save(user);
  }

  private ClientModel createClient(UserModel user) {
    ClientModel client = new ClientModel();
    client.setUser(user);
    return clients.save(client);
  }

  private AddressModel createAddress() {
    AddressModel address = new AddressModel();
    address.setCity(city);
    address.setZipCode("13015904");
    address.setStreet("Rua da Casa");
    address.setNumber("10");
    return addresses.save(address);
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
