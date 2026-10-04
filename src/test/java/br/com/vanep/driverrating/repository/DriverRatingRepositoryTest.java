package br.com.vanep.driverrating.repository;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.clientdriver.enums.RelationshipStatus;
import br.com.vanep.clientdriver.model.ClientDriverModel;
import br.com.vanep.clientdriver.repository.ClientDriverRepository;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.driverrating.model.DriverRatingModel;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class DriverRatingRepositoryTest {

  @Autowired private DriverRatingRepository repository;
  @Autowired private ClientDriverRepository links;
  @Autowired private ClientRepository clients;
  @Autowired private DriverRepository drivers;
  @Autowired private UserRepository users;

  private DriverModel carlos;

  @BeforeEach
  void setUp() {
    carlos = createDriver("carlos@vanep.com", "52998224725");
  }

  @Test
  void averagesTheRatingsOfTheDriver() {
    rate(link(createClient("maria@vanep.com", "11144477735")), "5.00");
    rate(link(createClient("bruno@vanep.com", "12345678909")), "4.00");

    assertThat(repository.calculateAverageRatingForDriver(carlos.getId()))
        .get()
        .satisfies(average -> assertThat(average).isEqualByComparingTo("4.50"));
  }

  @Test
  void aDriverWithoutRatingsHasNoAverage() {
    assertThat(repository.calculateAverageRatingForDriver(carlos.getId())).isEmpty();
  }

  @Test
  void aRatingOnARemovedLinkDoesNotCount() {
    rate(link(createClient("maria@vanep.com", "11144477735")), "5.00");
    ClientDriverModel removed = link(createClient("bruno@vanep.com", "12345678909"));
    rate(removed, "1.00");

    links.delete(removed);

    assertThat(repository.calculateAverageRatingForDriver(carlos.getId()))
        .get()
        .satisfies(average -> assertThat(average).isEqualByComparingTo("5.00"));
  }

  private ClientDriverModel link(ClientModel client) {
    ClientDriverModel link = new ClientDriverModel();
    link.setClient(client);
    link.setDriver(carlos);
    link.setStatus(RelationshipStatus.ACTIVE);
    return links.save(link);
  }

  private void rate(ClientDriverModel link, String rating) {
    DriverRatingModel model = new DriverRatingModel();
    model.setLink(link);
    model.setRating(new BigDecimal(rating));
    repository.save(model);
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

  private ClientModel createClient(String email, String document) {
    ClientModel client = new ClientModel();
    client.setUser(createUser(UserType.CLIENT, "Cliente", email, document));
    return clients.save(client);
  }

  private DriverModel createDriver(String email, String document) {
    DriverModel driver = new DriverModel();
    driver.setUser(createUser(UserType.DRIVER, "Motorista", email, document));
    driver.setBasePrice(new BigDecimal("100.00"));
    return drivers.save(driver);
  }
}
