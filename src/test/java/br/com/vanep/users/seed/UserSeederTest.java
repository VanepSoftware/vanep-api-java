package br.com.vanep.users.seed;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.config.PasswordHasher;
import br.com.vanep.users.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

@ExtendWith(MockitoExtension.class)
class UserSeederTest {

  @Mock private UserRepository userRepository;
  @Mock private PasswordHasher passwordHasher;

  @InjectMocks private UserSeeder userSeeder;

  @Test
  void run_whenUsersExist_doesNotSave() {
    when(userRepository.count()).thenReturn(3L);

    userSeeder.run(new DefaultApplicationArguments());

    verify(userRepository, never()).saveAll(any());
    verify(passwordHasher, never()).encode(any());
  }

  @Test
  void run_whenEmpty_savesFiveUsersWithEncodedPassword() {
    when(userRepository.count()).thenReturn(0L);
    when(passwordHasher.encode("password123")).thenReturn("ENC");

    userSeeder.run(new DefaultApplicationArguments());

    verify(userRepository).saveAll(argThat(users -> ((List<?>) users).size() == 5));
    verify(passwordHasher).encode("password123");
  }
}
