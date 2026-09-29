package br.com.vanep.auth.web;

import br.com.vanep.assistant.model.AssistantModel;
import br.com.vanep.assistant.repository.AssistantRepository;
import br.com.vanep.auth.dto.AccountSignupRequestDTO;
import br.com.vanep.auth.dto.AssistantSignupRequestDTO;
import br.com.vanep.auth.dto.ClientSignupRequestDTO;
import br.com.vanep.auth.dto.DriverSignupFields;
import br.com.vanep.auth.dto.DriverSignupRequestDTO;
import br.com.vanep.auth.enums.AuthErrorCode;
import br.com.vanep.auth.exception.SignupDuplicateException;
import br.com.vanep.auth.validation.CpfValidator;
import br.com.vanep.auth.verification.EmailVerificationService;
import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.role.RoleName;
import br.com.vanep.role.repository.RoleRepository;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {
  private final UserRepository users;
  private final ClientRepository clients;
  private final DriverRepository drivers;
  private final AssistantRepository assistants;
  private final RoleRepository roles;
  private final PasswordEncoder passwordEncoder;
  private final EmailVerificationService emailVerification;

  public RegistrationService(
      UserRepository users,
      ClientRepository clients,
      DriverRepository drivers,
      AssistantRepository assistants,
      RoleRepository roles,
      PasswordEncoder passwordEncoder,
      EmailVerificationService emailVerification) {
    this.users = users;
    this.clients = clients;
    this.drivers = drivers;
    this.assistants = assistants;
    this.roles = roles;
    this.passwordEncoder = passwordEncoder;
    this.emailVerification = emailVerification;
  }

  @Transactional
  public UserModel registerClient(ClientSignupRequestDTO request) {
    UserModel user = createUser(UserType.CLIENT, RoleName.CLIENT, request);
    createRoleRecord(user, UserType.CLIENT, null);
    emailVerification.startVerification(user);
    return user;
  }

  @Transactional
  public UserModel registerDriver(DriverSignupRequestDTO request) {
    UserModel user = createUser(UserType.DRIVER, RoleName.DRIVER, request);
    createRoleRecord(user, UserType.DRIVER, request);
    emailVerification.startVerification(user);
    return user;
  }

  @Transactional
  public UserModel registerAssistant(AssistantSignupRequestDTO request) {
    UserModel user = createUser(UserType.ASSISTANT, RoleName.ASSISTANT, request);
    createRoleRecord(user, UserType.ASSISTANT, null);
    emailVerification.startVerification(user);
    return user;
  }

  /** Shared with the Google sign-up completion, so every channel creates the same role record. */
  public void createRoleRecord(UserModel user, UserType type, DriverSignupFields driverFields) {
    switch (type) {
      case CLIENT -> {
        ClientModel client = new ClientModel();
        client.setUser(user);
        clients.save(client);
      }
      case DRIVER -> {
        DriverModel driver = new DriverModel();
        driver.setUser(user);
        if (driverFields != null) {
          driver.setCnpj(driverFields.getCnpj());
          driver.setExperienceYears(driverFields.getExperienceYears());
          driver.setBasePrice(driverFields.getBasePrice());
        }
        driver.setApprovalStatus(DriverApprovalStatus.PENDING);
        drivers.save(driver);
      }
      case ASSISTANT -> {
        AssistantModel assistant = new AssistantModel();
        assistant.setUser(user);
        assistants.save(assistant);
      }
      case ADMIN ->
          throw new IllegalArgumentException("Admin accounts are not created by sign-up.");
    }
  }

  UserModel createUser(UserType type, RoleName roleName, AccountSignupRequestDTO request) {
    String document = CpfValidator.normalize(request.getDocument());
    rejectDuplicates(request.getEmail(), document);
    UserModel user = new UserModel();
    user.setType(type);
    roles.findByRoleName(roleName).ifPresent(role -> user.setRoleId(role.getId()));
    user.setName(request.getName());
    user.setEmail(request.getEmail());
    user.setPassword(passwordEncoder.encode(request.getPassword()));
    user.setDocument(document);
    user.setPhone(request.getPhone());
    user.setBirthDate(request.getBirthDate());
    user.setGender(request.getGender());
    user.setVerified(false);
    user.setTermsAcceptedAt(Instant.now());
    return users.save(user);
  }

  public void rejectDuplicates(String email, String normalizedDocument) {
    if (email != null && users.existsByEmail(email)) {
      throw new SignupDuplicateException(
          "email", "auth.signup.email.duplicate", AuthErrorCode.EMAIL_DUPLICATE);
    }
    if (!normalizedDocument.isEmpty() && users.existsByDocument(normalizedDocument)) {
      throw new SignupDuplicateException(
          "document", "auth.signup.document.duplicate", AuthErrorCode.DOCUMENT_DUPLICATE);
    }
  }
}
