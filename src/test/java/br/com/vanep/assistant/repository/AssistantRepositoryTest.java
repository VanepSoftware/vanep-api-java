package br.com.vanep.assistant.repository;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.assistant.enums.AssistantStatus;
import br.com.vanep.assistant.enums.VerificationStatus;
import br.com.vanep.assistant.model.AssistantModel;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.UserType;
import br.com.vanep.user.model.UserModel;
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
class AssistantRepositoryTest {

  @Autowired private AssistantRepository assistants;
  @Autowired private UserRepository users;
  @Autowired private DriverRepository drivers;

  private UserModel assistantUser;
  private DriverModel driver;

  @BeforeEach
  void setUp() {
    UserModel driverUser = new UserModel();
    driverUser.setType(UserType.DRIVER);
    driverUser.setName("Driver One");
    driverUser.setEmail("driver1@vanep.com");
    driverUser.setDocument("11111111111");
    driverUser.setVerified(true);
    driverUser.setTermsAcceptedAt(Instant.now());
    driverUser = users.save(driverUser);

    driver = new DriverModel();
    driver.setUser(driverUser);
    driver.setBasePrice(new BigDecimal("100.00"));
    driver = drivers.save(driver);

    assistantUser = new UserModel();
    assistantUser.setType(UserType.CLIENT);
    assistantUser.setName("Assistant One");
    assistantUser.setEmail("assistant1@vanep.com");
    assistantUser.setDocument("22222222222");
    assistantUser.setVerified(true);
    assistantUser.setTermsAcceptedAt(Instant.now());
    assistantUser = users.save(assistantUser);
  }

  @Test
  void saveGeneratesTokenAndDefaults() {
    AssistantModel assistant = new AssistantModel();
    assistant.setUser(assistantUser);

    AssistantModel saved = assistants.saveAndFlush(assistant);

    assertThat(saved.getToken()).isNotBlank().hasSize(25);
    assertThat(saved.getStatus()).isEqualTo(AssistantStatus.UNLINKED);
    assertThat(saved.getVerificationStatus()).isEqualTo(VerificationStatus.PENDING);
    assertThat(saved.getDriver()).isNull();
    assertThat(saved.getActivatedAt()).isNull();
  }

  @Test
  void findByUserIdAndToken() {
    AssistantModel assistant = new AssistantModel();
    assistant.setUser(assistantUser);
    assistant = assistants.save(assistant);

    assertThat(assistants.findByUserId(assistantUser.getId())).isPresent();
    assertThat(assistants.findByToken(assistant.getToken())).isPresent();
  }

  @Test
  void softDeletedAssistantIsAbsentFromDefaultQueries() {
    AssistantModel assistant = new AssistantModel();
    assistant.setUser(assistantUser);
    assistant = assistants.save(assistant);

    assistants.delete(assistant);

    assertThat(assistants.findByToken(assistant.getToken())).isEmpty();
    assertThat(assistants.findAll()).isEmpty();
  }

  @Test
  void saveActiveAssistantLinkedToDriver() {
    AssistantModel assistant = new AssistantModel();
    assistant.setUser(assistantUser);
    assistant.setDriver(driver);
    assistant.setStatus(AssistantStatus.ACTIVE);
    assistant.setActivatedAt(Instant.now());

    AssistantModel saved = assistants.saveAndFlush(assistant);

    assertThat(saved.getDriver().getId()).isEqualTo(driver.getId());
    assertThat(saved.getStatus()).isEqualTo(AssistantStatus.ACTIVE);
    assertThat(saved.getActivatedAt()).isNotNull();
  }
}
