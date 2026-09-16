package br.com.vanep.auth.verification;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.auth.verification.model.EmailVerificationTokenModel;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class EmailVerificationTokenRepositoryTest {

  @Autowired private EmailVerificationTokenRepository tokens;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager entityManager;

  private Long userId;
  private Long otherUserId;

  @BeforeEach
  void setUp() {
    userId = persistUser("token-user@vanep.com", "39053344705");
    otherUserId = persistUser("other-token-user@vanep.com", "52998224725");
  }

  @Test
  void countsOnlyCodesIssuedInsideTheWindowForThatUser() {
    Instant now = Instant.now();
    persistToken(userId, now.minus(Duration.ofHours(2)), now.plus(Duration.ofHours(24)), null);
    persistToken(userId, now.minus(Duration.ofHours(30)), now.plus(Duration.ofHours(24)), null);
    persistToken(otherUserId, now.minus(Duration.ofHours(2)), now.plus(Duration.ofHours(24)), null);

    long issued = tokens.countByUserIdAndCreatedAtAfter(userId, now.minus(Duration.ofHours(24)));

    assertThat(issued).isEqualTo(1);
  }

  @Test
  void findsTheLastRowCreatedForTheUser() {
    Instant now = Instant.now();
    persistToken(
        userId, now.minus(Duration.ofMinutes(10)), now.plus(Duration.ofHours(24)), "older");
    persistToken(userId, now.minus(Duration.ofMinutes(1)), now.plus(Duration.ofHours(24)), "newer");

    EmailVerificationTokenModel latest =
        tokens.findFirstByUserIdOrderByCreatedAtDesc(userId).orElseThrow();

    assertThat(latest.getCodeHash()).isEqualTo("newer");
  }

  @Test
  @Transactional
  void locksTheMostRecentActiveRowOnly() {
    Instant now = Instant.now();
    persistToken(userId, now.minus(Duration.ofMinutes(9)), now.plus(Duration.ofHours(1)), "older");
    persistToken(userId, now.minus(Duration.ofMinutes(7)), now.plus(Duration.ofHours(1)), "active");
    persistToken(
        userId, now.minus(Duration.ofMinutes(5)), now.minus(Duration.ofMinutes(1)), "expired");
    Long consumedId =
        persistToken(
            userId, now.minus(Duration.ofMinutes(2)), now.plus(Duration.ofHours(1)), "consumed");
    jdbc.update(
        "update email_verification_token set consumed_at = ? where id = ?",
        Timestamp.from(now.minus(Duration.ofMinutes(1))),
        consumedId);
    entityManager.clear();

    EmailVerificationTokenModel locked = tokens.lockLatestActive(userId, now).orElseThrow();

    assertThat(locked.getCodeHash()).isEqualTo("active");
  }

  @Test
  @Transactional
  void findsNoActiveRowWhenEveryCodeWasConsumed() {
    Instant now = Instant.now();
    persistToken(userId, now.minus(Duration.ofMinutes(5)), now.plus(Duration.ofHours(1)), "active");
    tokens.consumeAllActive(userId, now);
    entityManager.clear();

    assertThat(tokens.lockLatestActive(userId, now)).isEmpty();
  }

  private Long persistToken(Long user, Instant createdAt, Instant expiresAt, String codeHash) {
    EmailVerificationTokenModel token = new EmailVerificationTokenModel();
    token.setUserId(user);
    token.setTokenHash("hash-" + UUID.randomUUID());
    token.setCodeHash(codeHash);
    token.setExpiresAt(expiresAt);
    Long id = tokens.saveAndFlush(token).getId();
    jdbc.update(
        "update email_verification_token set created_at = ? where id = ?",
        Timestamp.from(createdAt),
        id);
    return id;
  }

  private Long persistUser(String email, String document) {
    UserModel user = new UserModel();
    user.setType(UserType.CLIENT);
    user.setName("Token Owner");
    user.setEmail(email);
    user.setDocument(document);
    user.setPassword("hashed");
    return users.saveAndFlush(user).getId();
  }
}
