package br.com.vanep.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.auth.verification.EmailVerificationService;
import br.com.vanep.client.Client;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.driver.Driver;
import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.role.RoleName;
import br.com.vanep.role.model.RoleModel;
import br.com.vanep.role.repository.RoleRepository;
import br.com.vanep.user.User;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.UserType;
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
    when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    when(passwordEncoder.encode(anyString())).thenReturn("hashed");
    return new RegistrationService(
        users, clients, drivers, roles, passwordEncoder, emailVerification);
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

    User user = service.registerClient(form);

    assertThat(user.getType()).isEqualTo(UserType.CLIENT);
    assertThat(user.getPassword()).isEqualTo("hashed");
    assertThat(user.getTermsAcceptedAt()).isNotNull();
    assertThat(user.getRoleId()).isEqualTo(2L);

    ArgumentCaptor<Client> client = ArgumentCaptor.forClass(Client.class);
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

    User user = service.registerDriver(form);

    assertThat(user.getType()).isEqualTo(UserType.DRIVER);
    assertThat(user.getRoleId()).isEqualTo(3L);

    ArgumentCaptor<Driver> driver = ArgumentCaptor.forClass(Driver.class);
    verify(drivers).save(driver.capture());
    assertThat(driver.getValue().getApprovalStatus()).isEqualTo(DriverApprovalStatus.PENDING);
    assertThat(driver.getValue().getBasePrice()).isEqualByComparingTo("120.00");
    assertThat(driver.getValue().getCity()).isEqualTo("Taguatinga");
  }
}
