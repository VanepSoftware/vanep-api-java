package br.com.vanep.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.auth.security.PermissionEnum;
import br.com.vanep.auth.security.PermissionRegistry;
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
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.UserType;
import br.com.vanep.user.model.UserModel;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class DataSeederTest {

  @Mock private UserRepository users;
  @Mock private ClientRepository clients;
  @Mock private DriverRepository drivers;
  @Mock private RoleRepository roles;
  @Mock private RolePermissionRepository rolePermissions;
  @Mock private DependentSeeder dependentSeeder;
  @Mock private PasswordEncoder passwordEncoder;

  private DataSeeder seeder;

  @BeforeEach
  void setUp() {
    seeder =
        new DataSeeder(
            users, clients, drivers, roles, rolePermissions, dependentSeeder, passwordEncoder);
    seeder.adminEmail = "admin@vanep.com.br";
    seeder.adminPassword = "password";
    seeder.adminDocument = "00000000000";
  }

  private RoleModel roleTaggedAs(RoleName roleName) {
    RoleModel role = new RoleModel();
    role.setId(1L);
    role.setName(roleName.name().toLowerCase());
    role.setRoleName(roleName);
    return role;
  }

  @Test
  void doesNothingWhenDisabled() {
    seeder.enabled = false;
    seeder.seedOnly = false;

    seeder.run(new DefaultApplicationArguments());

    verify(users, never()).save(any());
  }

  @Test
  void createsAdminBundleWithAllPermissionsWhenMissing() {
    seeder.enabled = true;
    when(roles.findByName(anyString())).thenReturn(Optional.empty());
    when(roles.save(any(RoleModel.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(users.existsByEmail(anyString())).thenReturn(true);
    when(users.findByTypeAndRoleIdIsNull(UserType.ADMIN)).thenReturn(List.of());
    when(roles.findByRoleName(RoleName.CLIENT))
        .thenReturn(Optional.of(roleTaggedAs(RoleName.CLIENT)));
    when(roles.findByRoleName(RoleName.DRIVER))
        .thenReturn(Optional.of(roleTaggedAs(RoleName.DRIVER)));
    when(roles.findByRoleName(RoleName.ASSISTANT))
        .thenReturn(Optional.of(roleTaggedAs(RoleName.ASSISTANT)));

    RoleModel adminRole = roleTaggedAs(RoleName.ADMIN);
    when(roles.findByRoleName(RoleName.ADMIN))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(adminRole));
    when(rolePermissions.save(any(RolePermissionModel.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    seeder.run(new DefaultApplicationArguments());

    ArgumentCaptor<RolePermissionModel> captor = ArgumentCaptor.forClass(RolePermissionModel.class);
    verify(rolePermissions, times(4)).save(captor.capture());
    RolePermissionModel adminBundle =
        captor.getAllValues().stream()
            .filter(bundle -> "ADMIN".equals(bundle.getName()))
            .findFirst()
            .orElseThrow();
    assertThat(adminBundle.getPermissions())
        .containsExactlyInAnyOrderElementsOf(PermissionRegistry.all());
    assertThat(adminRole.getRolePermission()).isEqualTo(adminBundle);
  }

  @Test
  void createsClientBundleWithDependentPermissionsWhenMissing() {
    seeder.enabled = true;
    RoleModel adminRole = roleTaggedAs(RoleName.ADMIN);
    adminRole.setRolePermission(new RolePermissionModel());
    RoleModel clientRole = roleTaggedAs(RoleName.CLIENT);
    RoleModel driverRole = roleTaggedAs(RoleName.DRIVER);
    RoleModel assistantRole = roleTaggedAs(RoleName.ASSISTANT);
    when(roles.findByRoleName(RoleName.ADMIN)).thenReturn(Optional.of(adminRole));
    when(roles.findByRoleName(RoleName.CLIENT)).thenReturn(Optional.of(clientRole));
    when(roles.findByRoleName(RoleName.DRIVER)).thenReturn(Optional.of(driverRole));
    when(roles.findByRoleName(RoleName.ASSISTANT)).thenReturn(Optional.of(assistantRole));
    when(users.existsByEmail(anyString())).thenReturn(true);
    when(users.findByTypeAndRoleIdIsNull(UserType.ADMIN)).thenReturn(List.of());
    when(rolePermissions.save(any(RolePermissionModel.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    seeder.run(new DefaultApplicationArguments());

    ArgumentCaptor<RolePermissionModel> captor = ArgumentCaptor.forClass(RolePermissionModel.class);
    verify(rolePermissions, times(3)).save(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(RolePermissionModel::getName)
        .containsExactlyInAnyOrder("CLIENT", "ASSISTANT", "DRIVER");
  }

  @Test
  void seedingIsIdempotentWhenAdminBundleAndRolesAlreadyExist() {
    seeder.enabled = true;
    RoleModel adminRole = roleTaggedAs(RoleName.ADMIN);
    adminRole.setRolePermission(new RolePermissionModel());
    RoleModel clientRole = roleTaggedAs(RoleName.CLIENT);
    clientRole.setRolePermission(new RolePermissionModel());
    RoleModel driverRole = roleTaggedAs(RoleName.DRIVER);
    driverRole.setRolePermission(new RolePermissionModel());
    RoleModel assistantRole = roleTaggedAs(RoleName.ASSISTANT);
    assistantRole.setRolePermission(new RolePermissionModel());
    when(roles.findByRoleName(RoleName.ADMIN)).thenReturn(Optional.of(adminRole));
    when(roles.findByRoleName(RoleName.CLIENT)).thenReturn(Optional.of(clientRole));
    when(roles.findByRoleName(RoleName.DRIVER)).thenReturn(Optional.of(driverRole));
    when(roles.findByRoleName(RoleName.ASSISTANT)).thenReturn(Optional.of(assistantRole));
    when(users.existsByEmail(anyString())).thenReturn(true);
    when(users.findByTypeAndRoleIdIsNull(UserType.ADMIN)).thenReturn(List.of());

    seeder.run(new DefaultApplicationArguments());
    seeder.run(new DefaultApplicationArguments());

    verify(rolePermissions, never()).save(any());
    verify(roles, never()).save(any());
  }

  @Test
  void createsAdminWhenEnabledAndMissing() {
    seeder.enabled = true;
    RoleModel adminRole = roleTaggedAs(RoleName.ADMIN);
    adminRole.setRolePermission(new RolePermissionModel());
    when(roles.findByRoleName(RoleName.ADMIN)).thenReturn(Optional.of(adminRole));
    when(roles.findByRoleName(RoleName.CLIENT))
        .thenReturn(Optional.of(roleTaggedAs(RoleName.CLIENT)));
    when(roles.findByRoleName(RoleName.DRIVER))
        .thenReturn(Optional.of(roleTaggedAs(RoleName.DRIVER)));
    when(roles.findByRoleName(RoleName.ASSISTANT))
        .thenReturn(Optional.of(roleTaggedAs(RoleName.ASSISTANT)));
    when(users.existsByEmail(anyString())).thenReturn(false);
    when(users.findByTypeAndRoleIdIsNull(UserType.ADMIN)).thenReturn(List.of());
    when(passwordEncoder.encode(anyString())).thenReturn("hashed");

    seeder.run(new DefaultApplicationArguments());

    verify(users, atLeastOnce()).save(any(UserModel.class));
  }

  @Test
  void createsApprovedDriverAndAssignsDriverRole() {
    seeder.enabled = true;
    RoleModel adminRole = roleTaggedAs(RoleName.ADMIN);
    adminRole.setRolePermission(new RolePermissionModel());
    RoleModel driverRole = roleTaggedAs(RoleName.DRIVER);
    when(roles.findByRoleName(RoleName.ADMIN)).thenReturn(Optional.of(adminRole));
    when(roles.findByRoleName(RoleName.CLIENT))
        .thenReturn(Optional.of(roleTaggedAs(RoleName.CLIENT)));
    when(roles.findByRoleName(RoleName.DRIVER)).thenReturn(Optional.of(driverRole));
    when(roles.findByRoleName(RoleName.ASSISTANT))
        .thenReturn(Optional.of(roleTaggedAs(RoleName.ASSISTANT)));
    when(users.existsByEmail(anyString())).thenReturn(false);
    when(users.findByTypeAndRoleIdIsNull(UserType.ADMIN)).thenReturn(List.of());
    when(passwordEncoder.encode(anyString())).thenReturn("hashed");

    seeder.run(new DefaultApplicationArguments());

    ArgumentCaptor<DriverModel> captor = ArgumentCaptor.forClass(DriverModel.class);
    verify(drivers, times(1)).save(captor.capture());
    assertThat(captor.getValue().getApprovalStatus()).isEqualTo(DriverApprovalStatus.APPROVED);
    assertThat(captor.getValue().getUser().getRoleId()).isEqualTo(driverRole.getId());
  }

  @Test
  void createsAssistantBundleWithProfilePermissionsWhenMissing() {
    seeder.enabled = true;
    RoleModel adminRole = roleTaggedAs(RoleName.ADMIN);
    adminRole.setRolePermission(new RolePermissionModel());
    RoleModel clientRole = roleTaggedAs(RoleName.CLIENT);
    clientRole.setRolePermission(new RolePermissionModel());
    RoleModel driverRole = roleTaggedAs(RoleName.DRIVER);
    driverRole.setRolePermission(new RolePermissionModel());
    RoleModel assistantRole = roleTaggedAs(RoleName.ASSISTANT);
    when(roles.findByRoleName(RoleName.ADMIN)).thenReturn(Optional.of(adminRole));
    when(roles.findByRoleName(RoleName.CLIENT)).thenReturn(Optional.of(clientRole));
    when(roles.findByRoleName(RoleName.DRIVER)).thenReturn(Optional.of(driverRole));
    when(roles.findByRoleName(RoleName.ASSISTANT)).thenReturn(Optional.of(assistantRole));
    when(users.existsByEmail(anyString())).thenReturn(true);
    when(users.findByTypeAndRoleIdIsNull(UserType.ADMIN)).thenReturn(List.of());
    when(rolePermissions.save(any(RolePermissionModel.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    seeder.run(new DefaultApplicationArguments());

    ArgumentCaptor<RolePermissionModel> captor = ArgumentCaptor.forClass(RolePermissionModel.class);
    verify(rolePermissions).save(captor.capture());
    assertThat(captor.getValue().getName()).isEqualTo("ASSISTANT");
    assertThat(captor.getValue().getPermissions())
        .containsExactlyInAnyOrder(
            PermissionEnum.SHOW_ASSISTANT.value(),
            PermissionEnum.UPDATE_ASSISTANT.value(),
            PermissionEnum.REVOKE_ASSISTANT.value());
    assertThat(assistantRole.getRolePermission()).isEqualTo(captor.getValue());
  }

  @Test
  void createsDriverBundleWithAssistantManagementPermissionsWhenMissing() {
    seeder.enabled = true;
    RoleModel adminRole = roleTaggedAs(RoleName.ADMIN);
    adminRole.setRolePermission(new RolePermissionModel());
    RoleModel clientRole = roleTaggedAs(RoleName.CLIENT);
    clientRole.setRolePermission(new RolePermissionModel());
    RoleModel assistantRole = roleTaggedAs(RoleName.ASSISTANT);
    assistantRole.setRolePermission(new RolePermissionModel());
    RoleModel driverRole = roleTaggedAs(RoleName.DRIVER);
    when(roles.findByRoleName(RoleName.ADMIN)).thenReturn(Optional.of(adminRole));
    when(roles.findByRoleName(RoleName.CLIENT)).thenReturn(Optional.of(clientRole));
    when(roles.findByRoleName(RoleName.ASSISTANT)).thenReturn(Optional.of(assistantRole));
    when(roles.findByRoleName(RoleName.DRIVER)).thenReturn(Optional.of(driverRole));
    when(users.existsByEmail(anyString())).thenReturn(true);
    when(users.findByTypeAndRoleIdIsNull(UserType.ADMIN)).thenReturn(List.of());
    when(rolePermissions.save(any(RolePermissionModel.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    seeder.run(new DefaultApplicationArguments());

    ArgumentCaptor<RolePermissionModel> captor = ArgumentCaptor.forClass(RolePermissionModel.class);
    verify(rolePermissions).save(captor.capture());
    assertThat(captor.getValue().getName()).isEqualTo("DRIVER");
    assertThat(captor.getValue().getPermissions())
        .containsExactlyInAnyOrder(
            PermissionEnum.LIST_ASSISTANTS.value(),
            PermissionEnum.PAUSE_ASSISTANT.value(),
            PermissionEnum.RESUME_ASSISTANT.value(),
            PermissionEnum.REVOKE_ASSISTANT.value(),
            PermissionEnum.CREATE_ASSISTANT_INVITE.value(),
            PermissionEnum.CANCEL_ASSISTANT_INVITE.value());
    assertThat(driverRole.getRolePermission()).isEqualTo(captor.getValue());
  }
}
