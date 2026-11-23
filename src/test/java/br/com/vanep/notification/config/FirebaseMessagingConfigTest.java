package br.com.vanep.notification.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class FirebaseMessagingConfigTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(FirebaseMessagingConfig.class);

  @TempDir Path dir;

  @AfterEach
  void deleteFirebaseApps() {
    FirebaseApp.getApps().forEach(app -> app.delete());
  }

  @Test
  void withoutACredentialsPathThereIsNoMessagingClient() {
    runner.run(context -> assertThat(context).doesNotHaveBean(FirebaseMessaging.class));
  }

  @Test
  void aBlankCredentialsPathCountsAsUnset() {
    runner
        .withPropertyValues(FirebaseMessagingConfig.CREDENTIALS_PATH_PROPERTY + "=  ")
        .run(context -> assertThat(context).doesNotHaveBean(FirebaseMessaging.class));
  }

  @Test
  void aServiceAccountFileBuildsTheMessagingClientWithoutCallingGoogle() throws Exception {
    Path credentials = writeServiceAccount();

    runner
        .withPropertyValues(FirebaseMessagingConfig.CREDENTIALS_PATH_PROPERTY + "=" + credentials)
        .run(
            context -> {
              assertThat(context).hasSingleBean(FirebaseMessaging.class);
              assertThat(FirebaseApp.getInstance(FirebaseMessagingConfig.APP_NAME)).isNotNull();
            });
  }

  @Test
  void reusesTheAppAlreadyInitialized() throws Exception {
    Path credentials = writeServiceAccount();

    FirebaseApp first = FirebaseMessagingConfig.initializeApp(credentials);

    assertThat(FirebaseMessagingConfig.initializeApp(credentials)).isSameAs(first);
  }

  @Test
  void aMissingCredentialsFileFailsStartup() {
    runner
        .withPropertyValues(
            FirebaseMessagingConfig.CREDENTIALS_PATH_PROPERTY + "=" + dir.resolve("missing.json"))
        .run(context -> assertThat(context).hasFailed());
  }

  private Path writeServiceAccount() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    String key =
        Base64.getEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded());
    String json =
        """
        {
          "type": "service_account",
          "project_id": "vanep-test",
          "private_key_id": "test-key",
          "private_key": "-----BEGIN PRIVATE KEY-----\\n%s\\n-----END PRIVATE KEY-----\\n",
          "client_email": "push@vanep-test.iam.gserviceaccount.com",
          "client_id": "1",
          "token_uri": "http://localhost:1/token"
        }
        """
            .formatted(key);
    return Files.writeString(dir.resolve("service-account.json"), json);
  }
}
