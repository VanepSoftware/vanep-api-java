package br.com.vanep.auth.web;

import br.com.vanep.user.Client;
import br.com.vanep.user.ClientRepository;
import br.com.vanep.user.Driver;
import br.com.vanep.user.DriverApprovalStatus;
import br.com.vanep.user.DriverRepository;
import br.com.vanep.user.User;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.UserType;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cria contas (e o perfil cliente/motorista) a partir do cadastro por e-mail/senha. */
@Service
public class RegistrationService {

  private final UserRepository users;
  private final ClientRepository clients;
  private final DriverRepository drivers;
  private final PasswordEncoder passwordEncoder;

  public RegistrationService(
      UserRepository users,
      ClientRepository clients,
      DriverRepository drivers,
      PasswordEncoder passwordEncoder) {
    this.users = users;
    this.clients = clients;
    this.drivers = drivers;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional
  public User registerClient(ClientSignupForm form) {
    User user = createUser(UserType.CLIENT, form);
    Client client = new Client();
    client.setUser(user);
    clients.save(client);
    return user;
  }

  @Transactional
  public User registerDriver(DriverSignupForm form) {
    User user = createUser(UserType.DRIVER, form);
    Driver driver = new Driver();
    driver.setUser(user);
    driver.setCnpj(form.getCnpj());
    driver.setExperienceYears(form.getExperienceYears());
    driver.setCity(form.getCity());
    driver.setBasePrice(form.getBasePrice());
    driver.setApprovalStatus(DriverApprovalStatus.PENDING);
    drivers.save(driver);
    return user;
  }

  private User createUser(UserType type, AccountSignupForm form) {
    User user = new User();
    user.setType(type);
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
