package br.com.vanep.driver.repository;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.assistant.enums.AssistantStatus;
import br.com.vanep.assistant.model.AssistantModel;
import br.com.vanep.assistant.repository.AssistantRepository;
import br.com.vanep.auth.token.SecureTokens;
import br.com.vanep.driver.DriverLinkCodeStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverLinkCodeModel;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.UserType;
import br.com.vanep.user.model.UserModel;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class DriverLinkCodeRepositoryTest {

  @Autowired private DriverLinkCodeRepository linkCodes;
  @Autowired private DriverRepository drivers;
  @Autowired private UserRepository users;
  @Autowired private AssistantRepository assistants;

  private DriverModel driver;
  private String codeHash;

  @BeforeEach
  void setUp() {
    UserModel driverUser = new UserModel();
    driverUser.setType(UserType.DRIVER);
    driverUser.setName("Driver Link");
    driverUser.setEmail("driverlink@vanep.com");
    driverUser.setDocument("33333333333");
    driverUser.setVerified(true);
    driverUser.setTermsAcceptedAt(Instant.now());
    driverUser = users.save(driverUser);

    driver = new DriverModel();
    driver.setUser(driverUser);
    driver.setBasePrice(new BigDecimal("120.00"));
    driver = drivers.save(driver);

    codeHash = SecureTokens.hash("ABC234");
  }

  private DriverLinkCodeModel activeCode(Instant expiresAt) {
    DriverLinkCodeModel code = new DriverLinkCodeModel();
    code.setDriver(driver);
    code.setCodeHash(codeHash);
    code.setStatus(DriverLinkCodeStatus.ACTIVE);
    code.setExpiresAt(expiresAt);
    return linkCodes.saveAndFlush(code);
  }

  @Test
  void findByDriverIdAndStatus() {
    activeCode(Instant.now().plus(24, ChronoUnit.HOURS));

    assertThat(linkCodes.findByDriverIdAndStatus(driver.getId(), DriverLinkCodeStatus.ACTIVE))
        .isPresent();
  }

  @Test
  @Transactional
  void consumeIfActiveMarksCodeConsumed() {
    activeCode(Instant.now().plus(24, ChronoUnit.HOURS));

    UserModel assistantUser = new UserModel();
    assistantUser.setType(UserType.CLIENT);
    assistantUser.setName("Assistant Consume");
    assistantUser.setEmail("consume@vanep.com");
    assistantUser.setDocument("44444444444");
    assistantUser.setVerified(true);
    assistantUser.setTermsAcceptedAt(Instant.now());
    assistantUser = users.save(assistantUser);

    AssistantModel assistant = new AssistantModel();
    assistant.setUser(assistantUser);
    assistant.setStatus(AssistantStatus.UNLINKED);
    assistant = assistants.saveAndFlush(assistant);

    Instant now = Instant.now();
    int updated = linkCodes.consumeIfActive(codeHash, assistant.getId(), now);

    assertThat(updated).isEqualTo(1);

    DriverLinkCodeModel reloaded = linkCodes.findByCodeHash(codeHash).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(DriverLinkCodeStatus.CONSUMED);
    assertThat(reloaded.getConsumedAt()).isNotNull();
    assertThat(reloaded.getConsumedByAssistantId()).isEqualTo(assistant.getId());
  }

  @Test
  @Transactional
  void consumeIfActiveReturnsZeroWhenExpired() {
    activeCode(Instant.now().minus(1, ChronoUnit.HOURS));

    int updated = linkCodes.consumeIfActive(codeHash, 99L, Instant.now());

    assertThat(updated).isEqualTo(0);
    assertThat(linkCodes.findByCodeHash(codeHash).orElseThrow().getStatus())
        .isEqualTo(DriverLinkCodeStatus.ACTIVE);
  }

  @Test
  @Transactional
  void consumeIfActiveReturnsZeroWhenAlreadyConsumed() {
    DriverLinkCodeModel code = activeCode(Instant.now().plus(24, ChronoUnit.HOURS));
    code.setStatus(DriverLinkCodeStatus.CONSUMED);
    linkCodes.saveAndFlush(code);

    int updated = linkCodes.consumeIfActive(codeHash, 99L, Instant.now());

    assertThat(updated).isEqualTo(0);
  }
}
