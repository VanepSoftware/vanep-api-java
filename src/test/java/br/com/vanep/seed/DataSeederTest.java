package br.com.vanep.seed;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.user.User;
import br.com.vanep.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class DataSeederTest {

  @Mock private UserRepository users;
  @Mock private PasswordEncoder passwordEncoder;

  private DataSeeder seeder;

  @BeforeEach
  void setUp() {
    seeder = new DataSeeder(users, passwordEncoder);
    seeder.adminEmail = "admin@vanep.com.br";
    seeder.adminPassword = "password";
    seeder.adminDocument = "00000000000";
  }

  @Test
  void doesNothingWhenDisabled() {
    seeder.enabled = false;
    seeder.seedOnly = false;

    seeder.run(new DefaultApplicationArguments());

    verify(users, never()).save(any());
  }

  @Test
  void createsAdminWhenEnabledAndMissing() {
    seeder.enabled = true;
    when(users.existsByEmail("admin@vanep.com.br")).thenReturn(false);
    when(passwordEncoder.encode(anyString())).thenReturn("hashed");

    seeder.run(new DefaultApplicationArguments());

    verify(users).save(any(User.class));
  }

  @Test
  void skipsAdminWhenAlreadyPresent() {
    seeder.enabled = true;
    when(users.existsByEmail("admin@vanep.com.br")).thenReturn(true);

    seeder.run(new DefaultApplicationArguments());

    verify(users, never()).save(any());
  }
}
