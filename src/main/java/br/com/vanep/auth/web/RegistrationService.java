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
import br.com.vanep.auth.exception.SignupDuplicateException.DuplicateField;
import br.com.vanep.auth.security.UserPasswordService;
import br.com.vanep.auth.verification.EmailVerificationService;
import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.role.RoleName;
import br.com.vanep.role.repository.RoleRepository;
import br.com.vanep.shared.validation.CpfValidator;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {
  private final UserRepository users;
  private final ClientRepository clients;
  private final DriverRepository drivers;
  private final AssistantRepository assistants;
  private final RoleRepository roles;
  private final UserPasswordService userPasswords;
  private final EmailVerificationService emailVerification;

  public RegistrationService(
      UserRepository users,
      ClientRepository clients,
      DriverRepository drivers,
      AssistantRepository assistants,
      RoleRepository roles,
      UserPasswordService userPasswords,
      EmailVerificationService emailVerification) {
    this.users = users;
    this.clients = clients;
    this.drivers = drivers;
    this.assistants = assistants;
    this.roles = roles;
    this.userPasswords = userPasswords;
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
    rejectDuplicates(request.getEmail(), document, request.getPhone());
    UserModel user = new UserModel();
    user.setType(type);
    roles.findByRoleName(roleName).ifPresent(role -> user.setRoleId(role.getId()));
    user.setName(request.getName());
    user.setEmail(request.getEmail());
    userPasswords.assignEncodedPassword(user, request.getPassword());
    user.setDocument(document);
    user.setPhone(request.getPhone());
    user.setBirthDate(request.getBirthDate());
    user.setGender(request.getGender());
    user.setVerified(false);
    user.setTermsAcceptedAt(Instant.now());
    return users.save(user);
  }

  /** Checks every field before failing, so the answer lists all duplicates instead of the first. */
  public void rejectDuplicates(String email, String document, String phone) {
    List<DuplicateField> duplicates = new ArrayList<>();
    if (email != null && !email.isBlank() && users.existsByEmail(email.trim())) {
      duplicates.add(
          new DuplicateField(
              "email", "auth.signup.email.duplicate", AuthErrorCode.EMAIL_DUPLICATE));
    }
    String normalizedDocument = CpfValidator.normalize(document);
    if (!normalizedDocument.isEmpty() && users.existsByDocument(normalizedDocument)) {
      duplicates.add(
          new DuplicateField(
              "document", "auth.signup.document.duplicate", AuthErrorCode.DOCUMENT_DUPLICATE));
    }
    String phoneDigits = digitsOf(phone);
    if (!phoneDigits.isEmpty() && users.existsByPhone(phoneDigits)) {
      duplicates.add(
          new DuplicateField(
              "phone", "auth.signup.phone.duplicate", AuthErrorCode.PHONE_DUPLICATE));
    }
    if (!duplicates.isEmpty()) {
      throw new SignupDuplicateException(duplicates);
    }
  }

  private static String digitsOf(String raw) {
    return raw == null ? "" : raw.replaceAll("\\D", "");
  }
}
