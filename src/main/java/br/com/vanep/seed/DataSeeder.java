package br.com.vanep.seed;

import br.com.vanep.auth.security.PermissionEnum;
import br.com.vanep.auth.security.PermissionRegistry;
import br.com.vanep.city.seed.CitySeeder;
import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.dependent.seed.DependentSeeder;
import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.role.RoleName;
import br.com.vanep.role.model.RoleModel;
import br.com.vanep.role.repository.RoleRepository;
import br.com.vanep.rolepermission.model.RolePermissionModel;
import br.com.vanep.rolepermission.repository.RolePermissionRepository;
import br.com.vanep.school.seed.SchoolSeeder;
import br.com.vanep.state.seed.StateSeeder;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.UserType;
import br.com.vanep.user.model.UserModel;
import java.math.BigDecimal;
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
  private static final String ADMIN_BUNDLE_NAME = "ADMIN";
  private static final String CLIENT_BUNDLE_NAME = "CLIENT";
  private static final String ASSISTANT_BUNDLE_NAME = "ASSISTANT";
  private static final String DRIVER_BUNDLE_NAME = "DRIVER";

  private final UserRepository users;
  private final ClientRepository clients;
  private final DriverRepository drivers;
  private final RoleRepository roles;
  private final RolePermissionRepository rolePermissions;
  private final DependentSeeder dependentSeeder;
  private final SchoolSeeder schoolSeeder;
  private final StateSeeder stateSeeder;
  private final CitySeeder citySeeder;
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
      DriverRepository drivers,
      RoleRepository roles,
      RolePermissionRepository rolePermissions,
      DependentSeeder dependentSeeder,
      SchoolSeeder schoolSeeder,
      StateSeeder stateSeeder,
      CitySeeder citySeeder,
      PasswordEncoder passwordEncoder) {
    this.users = users;
    this.clients = clients;
    this.drivers = drivers;
    this.roles = roles;
    this.rolePermissions = rolePermissions;
    this.dependentSeeder = dependentSeeder;
    this.schoolSeeder = schoolSeeder;
    this.stateSeeder = stateSeeder;
    this.citySeeder = citySeeder;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!enabled && !seedOnly) {
      return;
    }
    seedRoles();
    seedAdminPermissions();
    seedClientPermissions();
    seedAssistantPermissions();
    seedDriverPermissions();
    seedAdmin();
    seedClients();
    seedDrivers();
    dependentSeeder.seed();
    schoolSeeder.seed();
    stateSeeder.seed();
    citySeeder.seed();
    if (seedOnly) {
      log.info("Seed-only: data seeded; the application will shut down.");
    }
  }

  private void seedRoles() {
    record RoleSeed(String name, String description, RoleName roleName) {}
    List<RoleSeed> seeds =
        List.of(
            new RoleSeed("admin", "Full system access", RoleName.ADMIN),
            new RoleSeed("client", "Standard client access", RoleName.CLIENT),
            new RoleSeed("driver", "Driver access", RoleName.DRIVER),
            new RoleSeed("assistant", "Assistant access", RoleName.ASSISTANT));

    for (RoleSeed seed : seeds) {
      if (roles.findByRoleName(seed.roleName()).isPresent()) continue;
      RoleModel role = roles.findByName(seed.name()).orElseGet(RoleModel::new);
      role.setName(seed.name());
      role.setDescription(seed.description());
      role.setRoleName(seed.roleName());
      roles.save(role);
      log.info("Seed: role tagged with role_name ({}).", seed.roleName());
    }
  }

  private void seedAdminPermissions() {
    RoleModel adminRole =
        roles
            .findByRoleName(RoleName.ADMIN)
            .orElseThrow(() -> new IllegalStateException("Seed: ADMIN role not found."));
    if (adminRole.getRolePermission() == null) {
      RolePermissionModel bundle = new RolePermissionModel();
      bundle.setName(ADMIN_BUNDLE_NAME);
      bundle.setPermissions(List.copyOf(PermissionRegistry.all()));
      bundle = rolePermissions.save(bundle);
      adminRole.setRolePermission(bundle);
      roles.save(adminRole);
      log.info("Seed: ADMIN bundle created with all permissions.");
    }
    backfillAdminRoleId(adminRole);
  }

  private void seedClientPermissions() {
    RoleModel clientRole =
        roles
            .findByRoleName(RoleName.CLIENT)
            .orElseThrow(() -> new IllegalStateException("Seed: CLIENT role not found."));
    if (clientRole.getRolePermission() == null) {
      RolePermissionModel bundle = new RolePermissionModel();
      bundle.setName(CLIENT_BUNDLE_NAME);
      bundle.setPermissions(List.copyOf(PermissionEnum.crudFor("dependents")));
      bundle = rolePermissions.save(bundle);
      clientRole.setRolePermission(bundle);
      roles.save(clientRole);
      log.info("Seed: CLIENT bundle created with dependents permissions.");
    }
  }

  private void seedAssistantPermissions() {
    RoleModel assistantRole =
        roles
            .findByRoleName(RoleName.ASSISTANT)
            .orElseThrow(() -> new IllegalStateException("Seed: ASSISTANT role not found."));
    if (assistantRole.getRolePermission() == null) {
      RolePermissionModel bundle = new RolePermissionModel();
      bundle.setName(ASSISTANT_BUNDLE_NAME);
      bundle.setPermissions(
          List.of(
              PermissionEnum.SHOW_ASSISTANT.value(),
              PermissionEnum.UPDATE_ASSISTANT.value(),
              PermissionEnum.REVOKE_ASSISTANT.value()));
      bundle = rolePermissions.save(bundle);
      assistantRole.setRolePermission(bundle);
      roles.save(assistantRole);
      log.info("Seed: ASSISTANT bundle created with profile permissions.");
    }
  }

  private void seedDriverPermissions() {
    RoleModel driverRole =
        roles
            .findByRoleName(RoleName.DRIVER)
            .orElseThrow(() -> new IllegalStateException("Seed: DRIVER role not found."));
    if (driverRole.getRolePermission() == null) {
      RolePermissionModel bundle = new RolePermissionModel();
      bundle.setName(DRIVER_BUNDLE_NAME);
      bundle.setPermissions(
          List.of(
              PermissionEnum.LIST_ASSISTANTS.value(),
              PermissionEnum.PAUSE_ASSISTANT.value(),
              PermissionEnum.RESUME_ASSISTANT.value(),
              PermissionEnum.REVOKE_ASSISTANT.value(),
              PermissionEnum.CREATE_ASSISTANT_INVITE.value(),
              PermissionEnum.CANCEL_ASSISTANT_INVITE.value()));
      bundle = rolePermissions.save(bundle);
      driverRole.setRolePermission(bundle);
      roles.save(driverRole);
      log.info("Seed: DRIVER bundle created with assistant management permissions.");
    }
  }

  private void backfillAdminRoleId(RoleModel adminRole) {
    users
        .findByTypeAndRoleIdIsNull(UserType.ADMIN)
        .forEach(
            admin -> {
              admin.setRoleId(adminRole.getId());
              users.save(admin);
              log.info("Seed: role_id of admin {} updated.", admin.getEmail());
            });
  }

  private void seedAdmin() {
    if (users.existsByEmail(adminEmail)) {
      log.info("Seed: admin user already exists ({}).", adminEmail);
      return;
    }
    RoleModel adminRole = roles.findByRoleName(RoleName.ADMIN).orElseThrow();
    UserModel admin = new UserModel();
    admin.setType(UserType.ADMIN);
    admin.setRoleId(adminRole.getId());
    admin.setName("Vanep Admin");
    admin.setEmail(adminEmail);
    admin.setPassword(passwordEncoder.encode(adminPassword));
    admin.setDocument(adminDocument);
    admin.setVerified(true);
    users.save(admin);
    log.info("Seed: admin user created ({}).", adminEmail);
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

    RoleModel clientRole = roles.findByRoleName(RoleName.CLIENT).orElseThrow();
    for (ClientSeed seed : seeds) {
      if (users.existsByEmail(seed.email())) continue;
      UserModel user = new UserModel();
      user.setType(UserType.CLIENT);
      user.setRoleId(clientRole.getId());
      user.setName(seed.name());
      user.setEmail(seed.email());
      user.setDocument(seed.document());
      user.setPassword(passwordEncoder.encode("password"));
      user.setVerified(true);
      user.setTermsAcceptedAt(Instant.now());
      users.save(user);
      ClientModel client = new ClientModel();
      client.setUser(user);
      clients.save(client);
      log.info("Seed: client created ({}).", seed.email());
    }
  }

  private void seedDrivers() {
    record DriverSeed(String name, String email, String document, String cnpj) {}
    List<DriverSeed> seeds =
        List.of(
            new DriverSeed(
                "Fabio Teixeira",
                "fabio.teixeira@seed.vanep.com.br",
                "66666666666",
                "11222333000181"),
            new DriverSeed(
                "Gustavo Santos",
                "gustavo.santos@seed.vanep.com.br",
                "77777777777",
                "22333444000192"),
            new DriverSeed(
                "Helena Costa", "helena.costa@seed.vanep.com.br", "88888888888", "33444555000103"));

    RoleModel driverRole = roles.findByRoleName(RoleName.DRIVER).orElseThrow();
    for (DriverSeed seed : seeds) {
      if (users.existsByEmail(seed.email())) continue;
      UserModel user = new UserModel();
      user.setType(UserType.DRIVER);
      user.setRoleId(driverRole.getId());
      user.setName(seed.name());
      user.setEmail(seed.email());
      user.setDocument(seed.document());
      user.setPassword(passwordEncoder.encode("password"));
      user.setVerified(true);
      user.setTermsAcceptedAt(Instant.now());
      users.save(user);
      DriverModel driver = new DriverModel();
      driver.setUser(user);
      driver.setCnpj(seed.cnpj());
      driver.setBasePrice(BigDecimal.valueOf(50));
      driver.setApprovalStatus(DriverApprovalStatus.APPROVED);
      drivers.save(driver);
      log.info("Seed: driver created ({}).", seed.email());
    }
  }
}
