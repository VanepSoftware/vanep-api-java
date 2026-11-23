package br.com.vanep.notification.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

@Configuration
public class FirebaseMessagingConfig {

  public static final String CREDENTIALS_PATH_PROPERTY = "vanep.push.firebase.credentials-path";
  static final String APP_NAME = "vanep";

  // A path that is set but unreadable fails startup on purpose: it is a broken deploy, not a
  // decision to run without push.
  @Bean
  @Conditional(CredentialsPathConfigured.class)
  public FirebaseMessaging firebaseMessaging(
      @Value("${" + CREDENTIALS_PATH_PROPERTY + "}") String credentialsPath) throws IOException {
    return FirebaseMessaging.getInstance(initializeApp(Path.of(credentialsPath)));
  }

  static FirebaseApp initializeApp(Path credentialsPath) throws IOException {
    try (InputStream credentials = Files.newInputStream(credentialsPath)) {
      FirebaseOptions options =
          FirebaseOptions.builder()
              .setCredentials(GoogleCredentials.fromStream(credentials))
              .build();
      return FirebaseApp.getApps().stream()
          .filter(app -> APP_NAME.equals(app.getName()))
          .findFirst()
          .orElseGet(() -> FirebaseApp.initializeApp(options, APP_NAME));
    }
  }

  static class CredentialsPathConfigured implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
      return StringUtils.hasText(context.getEnvironment().getProperty(CREDENTIALS_PATH_PROPERTY));
    }
  }
}
