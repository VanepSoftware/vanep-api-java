package br.com.vanep.seed;

import br.com.vanep.client.Client;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.role.Role;
import br.com.vanep.role.repository.RoleRepository;
import br.com.vanep.user.User;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.UserType;
import java.time.Instant;
import java.util.List;
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
  private final ClientRepository clients;
  private final RoleRepository roles;
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

  public DataSeeder(
      UserRepository users,
      ClientRepository clients,
      RoleRepository roles,
      PasswordEncoder passwordEncoder) {
    this.users = users;
    this.clients = clients;
    this.roles = roles;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!enabled && !seedOnly) {
      return;
    }
    seedRoles();
    seedAdmin();
    seedClients();
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

  private void seedRoles() {
    record RoleSeed(String name, String description) {}
    List<RoleSeed> seeds =
        List.of(
            new RoleSeed("admin", "Full system access"),
            new RoleSeed("client", "Standard client access"),
            new RoleSeed("driver", "Driver access"));

    for (RoleSeed seed : seeds) {
      if (roles.existsByName(seed.name())) continue;
      Role role = new Role();
      role.setName(seed.name());
      role.setDescription(seed.description());
      roles.save(role);
      log.info("Seed: role criada ({}).", seed.name());
    }
  }

  private void seedClients() {
    record ClientSeed(String name, String email, String document) {}
    List<ClientSeed> seeds =
        List.of(
            new ClientSeed("Ana Souza", "ana.souza@seed.vanep.com.br", "11111111111"),
            new ClientSeed("Bruno Lima", "bruno.lima@seed.vanep.com.br", "22222222222"),
            new ClientSeed("Carla Nunes", "carla.nunes@seed.vanep.com.br", "33333333333"),
            new ClientSeed("Diego Alves", "diego.alves@seed.vanep.com.br", "44444444444"),
            new ClientSeed("Elena Rocha", "elena.rocha@seed.vanep.com.br", "55555555555"));

    for (ClientSeed seed : seeds) {
      if (users.existsByEmail(seed.email())) continue;
      User user = new User();
      user.setType(UserType.CLIENT);
      user.setName(seed.name());
      user.setEmail(seed.email());
      user.setDocument(seed.document());
      user.setPassword(passwordEncoder.encode("password"));
      user.setVerified(true);
      user.setTermsAcceptedAt(Instant.now());
      users.save(user);
      Client client = new Client();
      client.setUser(user);
      clients.save(client);
      log.info("Seed: client criado ({}).", seed.email());
    }
  }
}
