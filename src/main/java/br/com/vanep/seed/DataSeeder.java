package br.com.vanep.seed;

import br.com.vanep.user.User;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.UserType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;

  @Value("${vanep.seed.enabled:false}")
  boolean enabled;

  @Value("${vanep.seed.only:false}")
  boolean seedOnly;

  @Value("${vanep.seed.admin.email:admin@vanep.com.br}")
  String adminEmail;

  @Value("${vanep.seed.admin.password:password}")
  String adminPassword;

  @Value("${vanep.seed.admin.document:00000000000}")
  String adminDocument;

  public DataSeeder(UserRepository users, PasswordEncoder passwordEncoder) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!enabled && !seedOnly) {
      return;
    }
    seedAdmin();
    if (seedOnly) {
      log.info("Seed-only: dados semeados; a aplicação será encerrada.");
    }
  }

  private void seedAdmin() {
    if (users.existsByEmail(adminEmail)) {
      log.info("Seed: usuário admin já existe ({}).", adminEmail);
      return;
    }
    User admin = new User();
    admin.setType(UserType.ADMIN);
    admin.setName("Vanep Admin");
    admin.setEmail(adminEmail);
    admin.setPassword(passwordEncoder.encode(adminPassword));
    admin.setDocument(adminDocument);
    admin.setVerified(true);
    users.save(admin);
    log.info("Seed: usuário admin criado ({}).", adminEmail);
  }
}
