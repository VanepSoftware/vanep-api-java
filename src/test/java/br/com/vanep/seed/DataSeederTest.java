package br.com.vanep.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.auth.security.PermissionRegistry;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.driver.Driver;
import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.role.RoleName;
import br.com.vanep.role.model.RoleModel;
import br.com.vanep.role.repository.RoleRepository;
import br.com.vanep.rolepermission.model.RolePermissionModel;
import br.com.vanep.rolepermission.repository.RolePermissionRepository;
import br.com.vanep.user.User;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.UserType;
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
  @Mock private PasswordEncoder passwordEncoder;

  private DataSeeder seeder;

  @BeforeEach
  void setUp() {
    seeder = new DataSeeder(users, clients, drivers, roles, rolePermissions, passwordEncoder);
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

    RoleModel adminRole = roleTaggedAs(RoleName.ADMIN);
    when(roles.findByRoleName(RoleName.ADMIN))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(adminRole));
    when(rolePermissions.save(any(RolePermissionModel.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    seeder.run(new DefaultApplicationArguments());

    ArgumentCaptor<RolePermissionModel> captor = ArgumentCaptor.forClass(RolePermissionModel.class);
    verify(rolePermissions).save(captor.capture());
    assertThat(captor.getValue().getPermissions())
        .containsExactlyInAnyOrderElementsOf(PermissionRegistry.all());
    assertThat(adminRole.getRolePermission()).isEqualTo(captor.getValue());
  }

  @Test
  void seedingIsIdempotentWhenAdminBundleAndRolesAlreadyExist() {
    seeder.enabled = true;
    RoleModel adminRole = roleTaggedAs(RoleName.ADMIN);
    adminRole.setRolePermission(new RolePermissionModel());
    when(roles.findByRoleName(RoleName.ADMIN)).thenReturn(Optional.of(adminRole));
    when(roles.findByRoleName(RoleName.CLIENT))
        .thenReturn(Optional.of(roleTaggedAs(RoleName.CLIENT)));
    when(roles.findByRoleName(RoleName.DRIVER))
        .thenReturn(Optional.of(roleTaggedAs(RoleName.DRIVER)));
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
    when(users.existsByEmail(anyString())).thenReturn(false);
    when(users.findByTypeAndRoleIdIsNull(UserType.ADMIN)).thenReturn(List.of());
    when(passwordEncoder.encode(anyString())).thenReturn("hashed");

    seeder.run(new DefaultApplicationArguments());

    verify(users, atLeastOnce()).save(any(User.class));
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
    when(users.existsByEmail(anyString())).thenReturn(false);
    when(users.findByTypeAndRoleIdIsNull(UserType.ADMIN)).thenReturn(List.of());
    when(passwordEncoder.encode(anyString())).thenReturn("hashed");

    seeder.run(new DefaultApplicationArguments());

    ArgumentCaptor<Driver> captor = ArgumentCaptor.forClass(Driver.class);
    verify(drivers, times(1)).save(captor.capture());
    assertThat(captor.getValue().getApprovalStatus()).isEqualTo(DriverApprovalStatus.APPROVED);
    assertThat(captor.getValue().getUser().getRoleId()).isEqualTo(driverRole.getId());
  }
}
