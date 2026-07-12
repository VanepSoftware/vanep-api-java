package br.com.vanep.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.assistant.enums.AssistantStatus;
import br.com.vanep.assistant.model.AssistantModel;
import br.com.vanep.assistant.repository.AssistantRepository;
import br.com.vanep.assistant.service.DriverLinkCodeConsumer;
import br.com.vanep.assistant.service.InvalidDriverLinkCodeException;
import br.com.vanep.auth.verification.EmailVerificationService;
import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.role.RoleName;
import br.com.vanep.role.model.RoleModel;
import br.com.vanep.role.repository.RoleRepository;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.UserType;
import br.com.vanep.user.model.UserModel;
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
  @Mock private DriverLinkCodeConsumer linkCodeConsumer;

  private RoleModel roleTaggedAs(RoleName roleName, long id) {
    RoleModel role = new RoleModel();
    role.setId(id);
    role.setName(roleName.name().toLowerCase());
    role.setRoleName(roleName);
    return role;
  }

  private RegistrationService service() {
    when(users.save(any(UserModel.class))).thenAnswer(inv -> inv.getArgument(0));
    when(passwordEncoder.encode(anyString())).thenReturn("hashed");
    return new RegistrationService(
        users,
        clients,
        drivers,
        assistants,
        roles,
        passwordEncoder,
        emailVerification,
        linkCodeConsumer);
  }

  @Test
  void registerClientCreatesUserAndClientProfile() {
    RegistrationService service = service();
    when(roles.findByRoleName(RoleName.CLIENT))
        .thenReturn(Optional.of(roleTaggedAs(RoleName.CLIENT, 2L)));
    ClientSignupForm form = new ClientSignupForm();
    form.setName("Ana");
    form.setEmail("ana@vanep.com");
    form.setPassword("secret1");
    form.setDocument("11111111111");
    form.setAcceptTerms(true);

    UserModel user = service.registerClient(form);

    assertThat(user.getType()).isEqualTo(UserType.CLIENT);
    assertThat(user.getPassword()).isEqualTo("hashed");
    assertThat(user.getTermsAcceptedAt()).isNotNull();
    assertThat(user.getRoleId()).isEqualTo(2L);

    ArgumentCaptor<ClientModel> client = ArgumentCaptor.forClass(ClientModel.class);
    verify(clients).save(client.capture());
    assertThat(client.getValue().getUser()).isSameAs(user);
  }

  @Test
  void registerDriverCreatesUserAndPendingDriverProfile() {
    RegistrationService service = service();
    when(roles.findByRoleName(RoleName.DRIVER))
        .thenReturn(Optional.of(roleTaggedAs(RoleName.DRIVER, 3L)));
    DriverSignupForm form = new DriverSignupForm();
    form.setName("Bruno");
    form.setEmail("bruno@vanep.com");
    form.setPassword("secret1");
    form.setDocument("22222222222");
    form.setCity("Taguatinga");
    form.setBasePrice(new BigDecimal("120.00"));
    form.setExperienceYears(5);
    form.setAcceptTerms(true);

    UserModel user = service.registerDriver(form);

    assertThat(user.getType()).isEqualTo(UserType.DRIVER);
    assertThat(user.getRoleId()).isEqualTo(3L);

    ArgumentCaptor<DriverModel> driver = ArgumentCaptor.forClass(DriverModel.class);
    verify(drivers).save(driver.capture());
    assertThat(driver.getValue().getApprovalStatus()).isEqualTo(DriverApprovalStatus.PENDING);
    assertThat(driver.getValue().getBasePrice()).isEqualByComparingTo("120.00");
    assertThat(driver.getValue().getCity()).isEqualTo("Taguatinga");
  }

  @Test
  void registerAssistantWithoutLinkCodeCreatesUnlinkedProfile() {
    RegistrationService service = service();
    when(roles.findByRoleName(RoleName.ASSISTANT))
        .thenReturn(Optional.of(roleTaggedAs(RoleName.ASSISTANT, 4L)));
    when(assistants.save(any(AssistantModel.class))).thenAnswer(inv -> inv.getArgument(0));
    AssistantSignupForm form = new AssistantSignupForm();
    form.setName("Carla");
    form.setEmail("carla@vanep.com");
    form.setPassword("secret1");
    form.setDocument("55555555555");
    form.setAcceptTerms(true);

    UserModel user = service.registerAssistant(form);

    assertThat(user.getType()).isEqualTo(UserType.ASSISTANT);
    assertThat(user.getRoleId()).isEqualTo(4L);

    ArgumentCaptor<AssistantModel> assistant = ArgumentCaptor.forClass(AssistantModel.class);
    verify(assistants).save(assistant.capture());
    assertThat(assistant.getValue().getStatus()).isEqualTo(AssistantStatus.UNLINKED);
    assertThat(assistant.getValue().getDriver()).isNull();
    verify(linkCodeConsumer, never()).consumeAndActivate(any(), anyString());
  }

  @Test
  void registerAssistantWithValidLinkCodeConsumesAndActivates() {
    RegistrationService service = service();
    when(roles.findByRoleName(RoleName.ASSISTANT))
        .thenReturn(Optional.of(roleTaggedAs(RoleName.ASSISTANT, 4L)));
    when(assistants.save(any(AssistantModel.class))).thenAnswer(inv -> inv.getArgument(0));
    when(linkCodeConsumer.isActiveCode("ABC234")).thenReturn(true);
    DriverModel driver = new DriverModel();
    driver.setId(10L);
    when(linkCodeConsumer.consumeAndActivate(any(AssistantModel.class), eq("ABC234")))
        .thenAnswer(
            inv -> {
              AssistantModel assistant = inv.getArgument(0);
              assistant.setDriver(driver);
              assistant.setStatus(AssistantStatus.ACTIVE);
              return driver;
            });

    AssistantSignupForm form = new AssistantSignupForm();
    form.setName("Diana");
    form.setEmail("diana@vanep.com");
    form.setPassword("secret1");
    form.setDocument("66666666666");
    form.setLinkCode("ABC234");
    form.setAcceptTerms(true);

    UserModel user = service.registerAssistant(form);

    assertThat(user.getType()).isEqualTo(UserType.ASSISTANT);
    verify(linkCodeConsumer).isActiveCode("ABC234");
    verify(linkCodeConsumer).consumeAndActivate(any(AssistantModel.class), eq("ABC234"));
  }

  @Test
  void registerAssistantRejectsInvalidLinkCodeBeforeCreatingUser() {
    when(linkCodeConsumer.isActiveCode("BADCODE")).thenReturn(false);
    when(linkCodeConsumer.messageForInvalidCode()).thenReturn("Código inválido");
    RegistrationService service =
        new RegistrationService(
            users,
            clients,
            drivers,
            assistants,
            roles,
            passwordEncoder,
            emailVerification,
            linkCodeConsumer);

    AssistantSignupForm form = new AssistantSignupForm();
    form.setName("Elena");
    form.setEmail("elena@vanep.com");
    form.setPassword("secret1");
    form.setDocument("77777777777");
    form.setLinkCode("BADCODE");
    form.setAcceptTerms(true);

    assertThatThrownBy(() -> service.registerAssistant(form))
        .isInstanceOf(InvalidDriverLinkCodeException.class)
        .hasMessage("Código inválido");

    verify(users, never()).save(any());
    verify(assistants, never()).save(any());
  }
}
