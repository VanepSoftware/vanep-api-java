package br.com.vanep.contract.seed;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.clientdriver.enums.RelationshipStatus;
import br.com.vanep.clientdriver.model.ClientDriverModel;
import br.com.vanep.clientdriver.repository.ClientDriverRepository;
import br.com.vanep.clientdriver.seed.ClientDriverSeeder;
import br.com.vanep.contract.enums.ContractStatus;
import br.com.vanep.contract.model.ContractItemModel;
import br.com.vanep.contract.model.ContractModel;
import br.com.vanep.contract.repository.ContractItemRepository;
import br.com.vanep.contract.repository.ContractRepository;
import br.com.vanep.country.model.CountryModel;
import br.com.vanep.country.repository.CountryRepository;
import br.com.vanep.dependent.seed.DependentSeeder;
import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.school.seed.SchoolSeeder;
import br.com.vanep.shared.enums.OperationShift;
import br.com.vanep.shared.enums.RouteLeg;
import br.com.vanep.state.model.StateModel;
import br.com.vanep.state.repository.StateRepository;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ContractSeederTest {

  @Autowired private ContractSeeder contractSeeder;
  @Autowired private SchoolSeeder schoolSeeder;
  @Autowired private DependentSeeder dependentSeeder;
  @Autowired private ClientDriverSeeder clientDriverSeeder;
  @Autowired private ContractRepository contracts;
  @Autowired private ContractItemRepository items;
  @Autowired private ClientDriverRepository links;
  @Autowired private ClientRepository clients;
  @Autowired private DriverRepository drivers;
  @Autowired private UserRepository users;
  @Autowired private CityRepository cities;
  @Autowired private StateRepository states;
  @Autowired private CountryRepository countries;

  @BeforeEach
  void setUp() {
    createClient("ana.souza@seed.vanep.com.br", "39053344705");
    createApprovedDriver("fabio.teixeira@seed.vanep.com.br", "23456789092");
  }

  private void seedDependentsAndLink() {
    schoolSeeder.seed();
    dependentSeeder.seed();
    clientDriverSeeder.seed();
  }

  @Test
  void theSeededLinkIsPendingUntilItsContractIsSeeded() {
    seedDependentsAndLink();

    assertThat(seededLink().getStatus()).isEqualTo(RelationshipStatus.PENDING);
  }

  @Test
  void seedsAnActiveContractThatActivatesTheSeededLink() {
    createCity();
    seedDependentsAndLink();

    contractSeeder.seed();

    ClientDriverModel link = seededLink();
    List<ContractModel> linkContracts = contracts.findByClientDriverId(link.getId());
    assertThat(linkContracts)
        .singleElement()
        .extracting(contract -> contract.getStatus())
        .isEqualTo(ContractStatus.ACTIVE);
    assertThat(link.getStatus()).isEqualTo(RelationshipStatus.ACTIVE);

    List<ContractItemModel> contractItems =
        items.findByContractIdIn(List.of(linkContracts.getFirst().getId()));
    assertThat(contractItems)
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.getPickupZipCode()).isEqualTo("01001000");
              assertThat(item.getPickupStreet()).isEqualTo("Rua das Acácias");
              assertThat(item.getPickupNeighborhood()).isEqualTo("Centro");
            });
    assertThat(contractItems.getFirst().getSchedule().getSlots())
        .hasSize(10)
        .allSatisfy(
            slot ->
                assertThat(slot.getShift())
                    .isEqualTo(
                        slot.getLeg() == RouteLeg.OUTBOUND
                            ? OperationShift.MORNING
                            : OperationShift.AFTERNOON));
  }

  @Test
  void seedingTwiceKeepsASingleContract() {
    createCity();
    seedDependentsAndLink();

    contractSeeder.seed();
    contractSeeder.seed();

    assertThat(contracts.findByClientDriverId(seededLink().getId())).hasSize(1);
  }

  @Test
  void skipsWhenTheDependentHasNoSchoolOrAddress() {
    seedDependentsAndLink();

    contractSeeder.seed();

    assertThat(contracts.count()).isZero();
    assertThat(seededLink().getStatus()).isEqualTo(RelationshipStatus.PENDING);
  }

  private ClientDriverModel seededLink() {
    return links.findAll().getFirst();
  }

  private void createClient(String email, String document) {
    ClientModel client = new ClientModel();
    client.setUser(createUser(UserType.CLIENT, email, document));
    clients.save(client);
  }

  private void createApprovedDriver(String email, String document) {
    DriverModel driver = new DriverModel();
    driver.setUser(createUser(UserType.DRIVER, email, document));
    driver.setBasePrice(new BigDecimal("100.00"));
    driver.setApprovalStatus(DriverApprovalStatus.APPROVED);
    drivers.save(driver);
  }

  private UserModel createUser(UserType type, String email, String document) {
    UserModel user = new UserModel();
    user.setType(type);
    user.setName("Seed");
    user.setEmail(email);
    user.setDocument(document);
    user.setVerified(true);
    user.setTermsAcceptedAt(Instant.now());
    return users.save(user);
  }

  private void createCity() {
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

    CityModel city = new CityModel();
    city.setState(state);
    city.setName("São Paulo");
    city.setIbgeCode(SchoolSeeder.SEED_CITY_IBGE_CODE);
    cities.save(city);
  }
}
