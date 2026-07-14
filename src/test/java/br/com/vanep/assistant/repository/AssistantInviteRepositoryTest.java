package br.com.vanep.assistant.repository;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.assistant.enums.AssistantInviteStatus;
import br.com.vanep.assistant.model.AssistantInviteModel;
import br.com.vanep.assistant.model.AssistantModel;
import br.com.vanep.auth.token.SecureTokens;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.UserType;
import br.com.vanep.user.model.UserModel;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AssistantInviteRepositoryTest {

  @Autowired private AssistantInviteRepository inviteRepository;
  @Autowired private AssistantRepository assistantRepository;
  @Autowired private DriverRepository driverRepository;
  @Autowired private UserRepository users;

  @Test
  void saveAssignsPublicTokenAndFindsByTokenHash() {
    DriverModel driver = createDriver("driver@vanep.com", "12345678909");
    AssistantModel assistant = createAssistant("assistant@vanep.com", "98765432100");
    String rawSecret = SecureTokens.generate();
    AssistantInviteModel invite = buildInvite(driver, assistant, rawSecret);

    AssistantInviteModel saved = inviteRepository.save(invite);

    assertThat(saved.getToken()).isNotBlank().hasSize(25);
    assertThat(saved.getStatus()).isEqualTo(AssistantInviteStatus.PENDING);
    AssistantInviteModel byToken = inviteRepository.findByToken(saved.getToken()).orElseThrow();
    AssistantInviteModel byHash =
        inviteRepository.findByLinkTokenHash(SecureTokens.hash(rawSecret)).orElseThrow();
    assertThat(byToken.getId()).isEqualTo(saved.getId());
    assertThat(byHash.getId()).isEqualTo(saved.getId());
  }

  @Test
  void rejectedCooldownQueryDetectsRecentRejectionForSamePair() {
    DriverModel driver = createDriver("driver2@vanep.com", "11122233344");
    AssistantModel assistant = createAssistant("assistant2@vanep.com", "55566677788");
    Instant threeDaysAgo = Instant.now().minus(3, ChronoUnit.DAYS);

    AssistantInviteModel rejected = buildInvite(driver, assistant, SecureTokens.generate());
    rejected.setStatus(AssistantInviteStatus.REJECTED);
    rejected.setRespondedAt(threeDaysAgo);
    inviteRepository.save(rejected);

    Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);
    assertThat(
            inviteRepository.existsByDriverIdAndAssistantIdAndStatusAndRespondedAtGreaterThanEqual(
                driver.getId(), assistant.getId(), AssistantInviteStatus.REJECTED, sevenDaysAgo))
        .isTrue();
  }

  @Test
  void rejectedCooldownQueryIgnoresRejectionsOlderThanSevenDays() {
    DriverModel driver = createDriver("driver3@vanep.com", "22233344455");
    AssistantModel assistant = createAssistant("assistant3@vanep.com", "66677788899");
    Instant tenDaysAgo = Instant.now().minus(10, ChronoUnit.DAYS);

    AssistantInviteModel rejected = buildInvite(driver, assistant, SecureTokens.generate());
    rejected.setStatus(AssistantInviteStatus.REJECTED);
    rejected.setRespondedAt(tenDaysAgo);
    inviteRepository.save(rejected);

    Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);
    assertThat(
            inviteRepository.existsByDriverIdAndAssistantIdAndStatusAndRespondedAtGreaterThanEqual(
                driver.getId(), assistant.getId(), AssistantInviteStatus.REJECTED, sevenDaysAgo))
        .isFalse();
  }

  @Test
  void softDeletedInviteIsAbsentFromDefaultQueries() {
    DriverModel driver = createDriver("driver4@vanep.com", "33344455566");
    AssistantModel assistant = createAssistant("assistant4@vanep.com", "77788899900");
    AssistantInviteModel invite = buildInvite(driver, assistant, SecureTokens.generate());
    AssistantInviteModel saved = inviteRepository.save(invite);

    inviteRepository.delete(saved);

    assertThat(inviteRepository.findByToken(saved.getToken())).isEmpty();
    assertThat(inviteRepository.findAll()).isEmpty();
  }

  private DriverModel createDriver(String email, String document) {
    UserModel user = new UserModel();
    user.setType(UserType.DRIVER);
    user.setName("Driver");
    user.setEmail(email);
    user.setDocument(document);
    user.setVerified(true);
    user.setTermsAcceptedAt(Instant.now());
    user = users.save(user);

    DriverModel driver = new DriverModel();
    driver.setUser(user);
    driver.setBasePrice(new BigDecimal("100.00"));
    return driverRepository.save(driver);
  }

  private AssistantModel createAssistant(String email, String document) {
    UserModel user = new UserModel();
    user.setType(UserType.CLIENT);
    user.setName("Assistant User");
    user.setEmail(email);
    user.setDocument(document);
    user.setVerified(true);
    user.setTermsAcceptedAt(Instant.now());
    user = users.save(user);

    AssistantModel assistant = new AssistantModel();
    assistant.setUser(user);
    return assistantRepository.save(assistant);
  }

  private AssistantInviteModel buildInvite(
      DriverModel driver, AssistantModel assistant, String rawSecret) {
    AssistantInviteModel invite = new AssistantInviteModel();
    invite.setDriver(driver);
    invite.setAssistant(assistant);
    invite.setLinkTokenHash(SecureTokens.hash(rawSecret));
    invite.setExpiresAt(Instant.now().plus(72, ChronoUnit.HOURS));
    return invite;
  }
}
