package br.com.vanep.auth.web;

import br.com.vanep.auth.verification.EmailVerificationService;
import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.role.RoleName;
import br.com.vanep.role.repository.RoleRepository;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.UserType;
import br.com.vanep.user.model.UserModel;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

  private final UserRepository users;
  private final ClientRepository clients;
  private final DriverRepository drivers;
  private final RoleRepository roles;
  private final PasswordEncoder passwordEncoder;
  private final EmailVerificationService emailVerification;

  public RegistrationService(
      UserRepository users,
      ClientRepository clients,
      DriverRepository drivers,
      RoleRepository roles,
      PasswordEncoder passwordEncoder,
      EmailVerificationService emailVerification) {
    this.users = users;
    this.clients = clients;
    this.drivers = drivers;
    this.roles = roles;
    this.passwordEncoder = passwordEncoder;
    this.emailVerification = emailVerification;
  }

  @Transactional
  public UserModel registerClient(ClientSignupForm form) {
    UserModel user = createUser(UserType.CLIENT, RoleName.CLIENT, form);
    ClientModel client = new ClientModel();
    client.setUser(user);
    clients.save(client);
    emailVerification.startVerification(user);
    return user;
  }

  @Transactional
  public UserModel registerDriver(DriverSignupForm form) {
    UserModel user = createUser(UserType.DRIVER, RoleName.DRIVER, form);
    DriverModel driver = new DriverModel();
    driver.setUser(user);
    driver.setCnpj(form.getCnpj());
    driver.setExperienceYears(form.getExperienceYears());
    driver.setCity(form.getCity());
    driver.setBasePrice(form.getBasePrice());
    driver.setApprovalStatus(DriverApprovalStatus.PENDING);
    drivers.save(driver);
    emailVerification.startVerification(user);
    return user;
  }

  private UserModel createUser(UserType type, RoleName roleName, AccountSignupForm form) {
    UserModel user = new UserModel();
    user.setType(type);
    roles.findByRoleName(roleName).ifPresent(role -> user.setRoleId(role.getId()));
    user.setName(form.getName());
    user.setEmail(form.getEmail());
    user.setPassword(passwordEncoder.encode(form.getPassword()));
    user.setDocument(form.getDocument());
    user.setPhone(form.getPhone());
    user.setBirthDate(form.getBirthDate());
    user.setGender(form.getGender());
    user.setVerified(false);
    user.setTermsAcceptedAt(Instant.now());
    return users.save(user);
  }
}
