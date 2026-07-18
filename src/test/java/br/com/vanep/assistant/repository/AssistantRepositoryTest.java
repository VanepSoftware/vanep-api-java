package br.com.vanep.assistant.repository;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.assistant.enums.AssistantStatus;
import br.com.vanep.assistant.enums.VerificationStatus;
import br.com.vanep.assistant.model.AssistantModel;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.UserType;
import br.com.vanep.user.model.UserModel;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AssistantRepositoryTest {

  @Autowired private AssistantRepository repository;
  @Autowired private UserRepository users;

  @Test
  void saveAssignsPublicTokenAndDefaultStatus() {
    UserModel user = createUser("assistant@vanep.com", "11122233344");
    AssistantModel assistant = new AssistantModel();
    assistant.setUser(user);

    AssistantModel saved = repository.save(assistant);

    assertThat(saved.getToken()).isNotBlank().hasSize(25);
    assertThat(saved.getStatus()).isEqualTo(AssistantStatus.UNLINKED);
    assertThat(saved.getVerificationStatus()).isEqualTo(VerificationStatus.PENDING);
    assertThat(saved.getDriver()).isNull();
  }

  @Test
  void findByTokenAndUserIdLocateAssistant() {
    UserModel user = createUser("find@vanep.com", "22233344455");
    AssistantModel assistant = new AssistantModel();
    assistant.setUser(user);
    AssistantModel saved = repository.save(assistant);

    AssistantModel byToken = repository.findByToken(saved.getToken()).orElseThrow();
    AssistantModel byUserId = repository.findByUserId(user.getId()).orElseThrow();
    assertThat(byToken.getId()).isEqualTo(saved.getId());
    assertThat(byUserId.getId()).isEqualTo(saved.getId());
  }

  @Test
  void softDeletedAssistantIsAbsentFromDefaultQueries() {
    UserModel user = createUser("deleted@vanep.com", "33344455566");
    AssistantModel assistant = new AssistantModel();
    assistant.setUser(user);
    AssistantModel saved = repository.save(assistant);

    repository.delete(saved);

    assertThat(repository.findByToken(saved.getToken())).isEmpty();
    assertThat(repository.findByUserId(user.getId())).isEmpty();
    assertThat(repository.findAll()).isEmpty();
  }

  private UserModel createUser(String email, String document) {
    UserModel user = new UserModel();
    user.setType(UserType.CLIENT);
    user.setName("Test User");
    user.setEmail(email);
    user.setDocument(document);
    user.setVerified(true);
    user.setTermsAcceptedAt(Instant.now());
    return users.save(user);
  }
}
