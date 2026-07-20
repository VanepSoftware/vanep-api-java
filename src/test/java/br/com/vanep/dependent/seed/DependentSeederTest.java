package br.com.vanep.dependent.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.dependent.repository.DependentRepository;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.model.UserModel;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DependentSeederTest {

  private static final String SEED_CLIENT_EMAIL = "ana.souza@seed.vanep.com.br";

  @Mock private DependentRepository dependents;
  @Mock private ClientRepository clients;
  @Mock private UserRepository users;

  private DependentSeeder seeder;

  @BeforeEach
  void setUp() {
    seeder = new DependentSeeder(dependents, clients, users);
  }

  private UserModel seedUser() {
    UserModel user = new UserModel();
    user.setId(10L);
    user.setEmail(SEED_CLIENT_EMAIL);
    return user;
  }

  private ClientModel seedClient() {
    ClientModel client = new ClientModel();
    client.setId(100L);
    return client;
  }

  @Test
  void createsTwoDependentsWithExactlyOneDefault() {
    when(users.findByEmail(SEED_CLIENT_EMAIL)).thenReturn(Optional.of(seedUser()));
    when(clients.findByUserId(10L)).thenReturn(Optional.of(seedClient()));
    when(dependents.existsByDocument(anyString())).thenReturn(false);

    seeder.seed();

    ArgumentCaptor<DependentModel> captor = ArgumentCaptor.forClass(DependentModel.class);
    verify(dependents, times(2)).save(captor.capture());
    assertThat(captor.getAllValues()).allMatch(dependent -> dependent.getClientId().equals(100L));
    assertThat(
            captor.getAllValues().stream().filter(dependent -> dependent.isDefaultDependent()).count())
        .isEqualTo(1);
  }

  @Test
  void skipsWhenSeedClientMissing() {
    when(users.findByEmail(SEED_CLIENT_EMAIL)).thenReturn(Optional.empty());

    seeder.seed();

    verify(dependents, never()).save(any());
  }

  @Test
  void isIdempotentWhenDependentsAlreadyExist() {
    when(users.findByEmail(SEED_CLIENT_EMAIL)).thenReturn(Optional.of(seedUser()));
    when(clients.findByUserId(10L)).thenReturn(Optional.of(seedClient()));
    when(dependents.existsByDocument(anyString())).thenReturn(true);

    seeder.seed();

    verify(dependents, never()).save(any());
  }
}
