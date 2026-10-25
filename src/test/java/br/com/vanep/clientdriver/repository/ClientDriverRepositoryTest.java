package br.com.vanep.clientdriver.repository;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.clientdriver.enums.LinkOrigin;
import br.com.vanep.clientdriver.enums.RelationshipStatus;
import br.com.vanep.clientdriver.model.ClientDriverModel;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.shared.SqlStatementCounter;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ClientDriverRepositoryTest {

  @Autowired private ClientDriverRepository repository;
  @Autowired private ClientRepository clients;
  @Autowired private DriverRepository drivers;
  @Autowired private UserRepository users;
  @Autowired private EntityManagerFactory entityManagerFactory;
  @Autowired private TransactionTemplate transactions;

  private ClientModel maria;
  private DriverModel carlos;

  @BeforeEach
  void setUp() {
    maria = createClient("maria@vanep.com", "11144477735");
    carlos = createDriver("carlos@vanep.com", "52998224725");
  }

  @Test
  void findsTheLinkByPair() {
    ClientDriverModel saved = repository.save(newLink(maria, carlos));

    assertThat(repository.findByPair(maria.getId(), carlos.getId()))
        .get()
        .extracting(link -> link.getId())
        .isEqualTo(saved.getId());
  }

  @Test
  void locksTheLinkOfThePairOnly() {
    ClientDriverModel saved = repository.save(newLink(maria, carlos));
    DriverModel helena = createDriver("helena@vanep.com", "15350946056");

    Optional<ClientDriverModel> locked =
        transactions.execute(status -> repository.lockByPair(maria.getId(), carlos.getId()));
    Optional<ClientDriverModel> other =
        transactions.execute(status -> repository.lockByPair(maria.getId(), helena.getId()));

    assertThat(locked).get().extracting(link -> link.getId()).isEqualTo(saved.getId());
    assertThat(other).isEmpty();
  }

  @Test
  void generatesOpaqueTokenOnPersist() {
    ClientDriverModel saved = repository.save(newLink(maria, carlos));

    assertThat(saved.getToken()).isNotBlank();
    assertThat(saved.getToken()).doesNotContain("-");
    assertThat(repository.findByToken(saved.getToken())).isPresent();
  }

  @Test
  void aLinkIsBornPending() {
    ClientDriverModel saved = repository.save(newLink(maria, carlos));

    assertThat(saved.getStatus()).isEqualTo(RelationshipStatus.PENDING);
  }

  @Test
  void oneClientLinksToSeveralDrivers() {
    DriverModel helena = createDriver("helena@vanep.com", "15350946056");

    repository.save(newLink(maria, carlos));
    repository.save(newLink(maria, helena));

    assertThat(repository.findByClientUserId(maria.getUser().getId())).hasSize(2);
  }

  @Test
  void oneDriverLinksToSeveralClients() {
    ClientModel bruno = createClient("bruno@vanep.com", "12345678909");

    repository.save(newLink(maria, carlos));
    repository.save(newLink(bruno, carlos));

    assertThat(repository.findByDriverUserId(carlos.getUser().getId())).hasSize(2);
  }

  @Test
  void eachSideSeesOnlyItsOwnLinks() {
    ClientModel bruno = createClient("bruno@vanep.com", "12345678909");
    DriverModel helena = createDriver("helena@vanep.com", "15350946056");

    repository.save(newLink(maria, carlos));
    repository.save(newLink(bruno, helena));

    assertThat(repository.findByClientUserId(maria.getUser().getId())).hasSize(1);
    assertThat(repository.findByDriverUserId(helena.getUser().getId())).hasSize(1);
  }

  @Test
  void deactivatingKeepsTheLinkVisible() {
    ClientDriverModel saved = repository.save(newLink(maria, carlos));
    saved.setStatus(RelationshipStatus.INACTIVE);
    repository.save(saved);

    assertThat(repository.findByToken(saved.getToken()))
        .get()
        .extracting(link -> link.getStatus())
        .isEqualTo(RelationshipStatus.INACTIVE);
  }

  @Test
  void blockingKeepsTheLinkVisibleWithWhoBlockedItAndWhen() {
    Instant blockedAt = Instant.parse("2026-10-17T12:00:00Z");
    ClientDriverModel saved = repository.save(newLink(maria, carlos));
    saved.setStatus(RelationshipStatus.BLOCKED);
    saved.setBlockedBy(carlos.getUser());
    saved.setBlockedAt(blockedAt);
    repository.save(saved);

    ClientDriverModel reread = repository.findByToken(saved.getToken()).orElseThrow();
    assertThat(reread.getStatus()).isEqualTo(RelationshipStatus.BLOCKED);
    assertThat(reread.getBlockedBy().getId()).isEqualTo(carlos.getUser().getId());
    assertThat(reread.getBlockedAt()).isEqualTo(blockedAt);
    assertThat(repository.findByPair(maria.getId(), carlos.getId())).isPresent();
  }

  @Test
  void blocksALinkOnlyWhileItIsNotBlocked() {
    ClientDriverModel saved = repository.save(newLink(maria, carlos));
    Instant blockedAt = Instant.parse("2026-10-17T12:00:00Z");

    Integer first =
        transactions.execute(
            status -> repository.block(saved.getId(), maria.getUser().getId(), blockedAt));
    Integer second =
        transactions.execute(
            status -> repository.block(saved.getId(), carlos.getUser().getId(), Instant.now()));

    ClientDriverModel reread = repository.findByToken(saved.getToken()).orElseThrow();
    assertThat(first).isEqualTo(1);
    assertThat(second).isZero();
    assertThat(reread.getStatus()).isEqualTo(RelationshipStatus.BLOCKED);
    assertThat(reread.getBlockedBy().getId()).isEqualTo(maria.getUser().getId());
    assertThat(reread.getBlockedAt()).isEqualTo(blockedAt);
  }

  @Test
  void unblocksALinkOnlyForItsBlocker() {
    ClientDriverModel saved = repository.save(newLink(maria, carlos));
    transactions.execute(
        status -> repository.block(saved.getId(), carlos.getUser().getId(), Instant.now()));

    Integer byTheBlockedParty =
        transactions.execute(
            status -> repository.unblock(saved.getId(), maria.getUser().getId(), "PENDING"));
    Integer byTheBlocker =
        transactions.execute(
            status -> repository.unblock(saved.getId(), carlos.getUser().getId(), "INACTIVE"));
    Integer again =
        transactions.execute(
            status -> repository.unblock(saved.getId(), carlos.getUser().getId(), "PENDING"));

    ClientDriverModel reread = repository.findByToken(saved.getToken()).orElseThrow();
    assertThat(byTheBlockedParty).isZero();
    assertThat(byTheBlocker).isEqualTo(1);
    assertThat(again).isZero();
    assertThat(reread.getStatus()).isEqualTo(RelationshipStatus.INACTIVE);
    assertThat(reread.getBlockedBy()).isNull();
    assertThat(reread.getBlockedAt()).isNull();
  }

  @Test
  void listsBlockedLinksInOneStatementWhoeverBlockedThem() {
    SqlStatementCounter counter = new SqlStatementCounter(entityManagerFactory);
    for (int index = 1; index <= 3; index++) {
      ClientModel client = createClient("client" + index + "@vanep.com", "2000000000" + index);
      ClientDriverModel link = newLink(client, carlos);
      link.setStatus(RelationshipStatus.BLOCKED);
      link.setBlockedBy(index % 2 == 0 ? carlos.getUser() : client.getUser());
      link.setBlockedAt(Instant.now());
      repository.save(link);
    }

    long statements =
        counter.countStatements(() -> repository.findByDriverUserId(carlos.getUser().getId()));

    assertThat(statements).isEqualTo(1);
  }

  @Test
  void storesTheOriginTheLinkWasCreatedWith() {
    ClientDriverModel link = newLink(maria, carlos);
    link.setOrigin(LinkOrigin.DRIVER_INVITE);
    ClientDriverModel saved = repository.save(link);

    assertThat(repository.findByToken(saved.getToken()))
        .get()
        .extracting(stored -> stored.getOrigin())
        .isEqualTo(LinkOrigin.DRIVER_INVITE);
  }

  @Test
  void aLaterSaveDoesNotChangeTheOrigin() {
    ClientDriverModel saved = repository.save(newLink(maria, carlos));
    saved.setOrigin(LinkOrigin.DRIVER_INVITE);
    saved.setStatus(RelationshipStatus.INACTIVE);
    repository.save(saved);

    ClientDriverModel reread = repository.findByToken(saved.getToken()).orElseThrow();
    assertThat(reread.getOrigin()).isEqualTo(LinkOrigin.CLIENT_SEARCH);
    assertThat(reread.getStatus()).isEqualTo(RelationshipStatus.INACTIVE);
  }

  @Test
  void softDeletedLinkIsAbsentFromDefaultQueries() {
    ClientDriverModel saved = repository.save(newLink(maria, carlos));

    repository.delete(saved);

    assertThat(repository.findByToken(saved.getToken())).isEmpty();
    assertThat(repository.findByPair(maria.getId(), carlos.getId())).isEmpty();
    assertThat(repository.findByClientUserId(maria.getUser().getId())).isEmpty();
    assertThat(repository.findAll()).isEmpty();
  }

  @Test
  void allowsANewLinkAfterTheSamePairWasSoftDeleted() {
    ClientDriverModel removed = repository.save(newLink(maria, carlos));
    repository.delete(removed);

    ClientDriverModel recreated = repository.save(newLink(maria, carlos));

    assertThat(recreated.getId()).isNotEqualTo(removed.getId());
    assertThat(repository.findByPair(maria.getId(), carlos.getId()))
        .get()
        .extracting(link -> link.getId())
        .isEqualTo(recreated.getId());
  }

  private ClientDriverModel newLink(ClientModel client, DriverModel driver) {
    ClientDriverModel link = new ClientDriverModel();
    link.setClient(client);
    link.setDriver(driver);
    link.setOrigin(LinkOrigin.CLIENT_SEARCH);
    return link;
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
