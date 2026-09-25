package br.com.vanep.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.assistant.enums.AssistantStatus;
import br.com.vanep.assistant.model.AssistantModel;
import br.com.vanep.assistant.repository.AssistantRepository;
import br.com.vanep.auth.dto.AssistantSignupRequestDTO;
import br.com.vanep.auth.dto.ClientSignupRequestDTO;
import br.com.vanep.auth.dto.DriverSignupRequestDTO;
import br.com.vanep.auth.exception.SignupDuplicateException;
import br.com.vanep.auth.verification.EmailVerificationService;
import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.role.RoleName;
import br.com.vanep.role.model.RoleModel;
import br.com.vanep.role.repository.RoleRepository;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {
  @Mock private UserRepository users;
  @Mock private ClientRepository clients;
  @Mock private DriverRepository drivers;
  @Mock private AssistantRepository assistants;
  @Mock private RoleRepository roles;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private EmailVerificationService emailVerification;

  private RoleModel roleTaggedAs(RoleName roleName, long id) {
    RoleModel role = new RoleModel();
    role.setId(id);
    role.setName(roleName.name().toLowerCase());
    role.setRoleName(roleName);
    return role;
  }

  private RegistrationService service() {
    return new RegistrationService(
        users, clients, drivers, assistants, roles, passwordEncoder, emailVerification);
  }

  private RegistrationService serviceThatSaves() {
    when(users.save(any(UserModel.class))).thenAnswer(inv -> inv.getArgument(0));
    when(passwordEncoder.encode(anyString())).thenReturn("hashed");
    return service();
  }

  private ClientSignupRequestDTO clientRequest() {
    ClientSignupRequestDTO request = new ClientSignupRequestDTO();
    request.setName("Ana");
    request.setEmail("ana@vanep.com");
    request.setPassword("secret1");
    request.setDocument("39053344705");
    request.setAcceptTerms(true);
    return request;
  }

  @Test
  void registerClientCreatesUserAndClientProfile() {
    RegistrationService service = serviceThatSaves();
    when(roles.findByRoleName(RoleName.CLIENT))
        .thenReturn(Optional.of(roleTaggedAs(RoleName.CLIENT, 2L)));

    UserModel user = service.registerClient(clientRequest());

    assertThat(user.getType()).isEqualTo(UserType.CLIENT);
    assertThat(user.getPassword()).isEqualTo("hashed");
    assertThat(user.getTermsAcceptedAt()).isNotNull();
    assertThat(user.getRoleId()).isEqualTo(2L);

    ArgumentCaptor<ClientModel> client = ArgumentCaptor.forClass(ClientModel.class);
    verify(clients).save(client.capture());
    assertThat(client.getValue().getUser()).isSameAs(user);
    verify(emailVerification).startVerification(user);
  }

  @Test
  void registerDriverCreatesUserAndPendingDriverProfile() {
    RegistrationService service = serviceThatSaves();
    when(roles.findByRoleName(RoleName.DRIVER))
        .thenReturn(Optional.of(roleTaggedAs(RoleName.DRIVER, 3L)));
    DriverSignupRequestDTO request = new DriverSignupRequestDTO();
    request.setName("Bruno");
    request.setEmail("bruno@vanep.com");
    request.setPassword("secret1");
    request.setDocument("52998224725");
    request.setBasePrice(new BigDecimal("120.00"));
    request.setExperienceYears(5);
    request.setAcceptTerms(true);

    UserModel user = service.registerDriver(request);

    assertThat(user.getType()).isEqualTo(UserType.DRIVER);
    assertThat(user.getRoleId()).isEqualTo(3L);

    ArgumentCaptor<DriverModel> driver = ArgumentCaptor.forClass(DriverModel.class);
    verify(drivers).save(driver.capture());
    assertThat(driver.getValue().getApprovalStatus()).isEqualTo(DriverApprovalStatus.PENDING);
    assertThat(driver.getValue().getBasePrice()).isEqualByComparingTo("120.00");
    verify(emailVerification).startVerification(user);
  }

  @Test
  void registerAssistantCreatesUserAndUnlinkedProfile() {
    RegistrationService service = serviceThatSaves();
    when(roles.findByRoleName(RoleName.ASSISTANT))
        .thenReturn(Optional.of(roleTaggedAs(RoleName.ASSISTANT, 4L)));
    AssistantSignupRequestDTO request = new AssistantSignupRequestDTO();
    request.setName("Carla");
    request.setEmail("carla@vanep.com");
    request.setPassword("secret1");
    request.setDocument("11144477735");
    request.setAcceptTerms(true);

    UserModel user = service.registerAssistant(request);

    assertThat(user.getType()).isEqualTo(UserType.ASSISTANT);
    assertThat(user.getRoleId()).isEqualTo(4L);

    ArgumentCaptor<AssistantModel> assistant = ArgumentCaptor.forClass(AssistantModel.class);
    verify(assistants).save(assistant.capture());
    assertThat(assistant.getValue().getUser()).isSameAs(user);
    assertThat(assistant.getValue().getStatus()).isEqualTo(AssistantStatus.UNLINKED);
    assertThat(assistant.getValue().getDriver()).isNull();
    verify(emailVerification).startVerification(user);
  }

  @Test
  void registerStoresNormalizedDocument() {
    RegistrationService service = serviceThatSaves();
    when(roles.findByRoleName(RoleName.CLIENT))
        .thenReturn(Optional.of(roleTaggedAs(RoleName.CLIENT, 2L)));
    ClientSignupRequestDTO request = clientRequest();
    request.setDocument("390.533.447-05");

    UserModel user = service.registerClient(request);

    assertThat(user.getDocument()).isEqualTo("39053344705");
  }

  @Test
  void registerRejectsDuplicateEmail() {
    RegistrationService service = service();
    when(users.existsByEmail("ana@vanep.com")).thenReturn(true);

    assertThatThrownBy(() -> service.registerClient(clientRequest()))
        .isInstanceOf(SignupDuplicateException.class)
        .extracting("field", "messageKey")
        .containsExactly("email", "auth.signup.email.duplicate");

    verify(users, never()).save(any(UserModel.class));
    verify(clients, never()).save(any(ClientModel.class));
  }

  @Test
  void registerRejectsDuplicateNormalizedDocument() {
    RegistrationService service = service();
    ClientSignupRequestDTO request = clientRequest();
    request.setDocument("390.533.447-05");
    when(users.existsByDocument("39053344705")).thenReturn(true);

    assertThatThrownBy(() -> service.registerClient(request))
        .isInstanceOf(SignupDuplicateException.class)
        .extracting("field", "messageKey")
        .containsExactly("document", "auth.signup.document.duplicate");

    verify(users, never()).save(any(UserModel.class));
  }

  @Test
  void createRoleRecordForDriverWithNullDriverFieldsSafeguardsAgainstNpeAndSetsPendingStatus() {
    RegistrationService service = service();
    UserModel user = new UserModel();

    service.createRoleRecord(user, UserType.DRIVER, null);

    ArgumentCaptor<DriverModel> driver = ArgumentCaptor.forClass(DriverModel.class);
    verify(drivers).save(driver.capture());
    assertThat(driver.getValue().getUser()).isSameAs(user);
    assertThat(driver.getValue().getBasePrice()).isNull();
    assertThat(driver.getValue().getApprovalStatus()).isEqualTo(DriverApprovalStatus.PENDING);
  }

  @Test
  void createRoleRecordForDriverSetsFieldsAndPendingStatus() {
    RegistrationService service = service();
    UserModel user = new UserModel();
    DriverSignupRequestDTO request = new DriverSignupRequestDTO();
    request.setCnpj("12345678000199");
    request.setExperienceYears(3);
    request.setBasePrice(new BigDecimal("150.00"));

    service.createRoleRecord(user, UserType.DRIVER, request);

    ArgumentCaptor<DriverModel> driver = ArgumentCaptor.forClass(DriverModel.class);
    verify(drivers).save(driver.capture());
    assertThat(driver.getValue().getUser()).isSameAs(user);
    assertThat(driver.getValue().getCnpj()).isEqualTo("12345678000199");
    assertThat(driver.getValue().getExperienceYears()).isEqualTo(3);
    assertThat(driver.getValue().getBasePrice()).isEqualByComparingTo("150.00");
    assertThat(driver.getValue().getApprovalStatus()).isEqualTo(DriverApprovalStatus.PENDING);
  }
}
