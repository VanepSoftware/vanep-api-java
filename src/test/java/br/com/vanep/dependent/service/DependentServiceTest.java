package br.com.vanep.dependent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.dependent.dto.DependentCreateDTO;
import br.com.vanep.dependent.dto.DependentResponseDTO;
import br.com.vanep.dependent.enums.Shift;
import br.com.vanep.dependent.mapper.DependentMapper;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.dependent.repository.DependentRepository;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.model.UserModel;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class DependentServiceTest {

  private static final String CLIENT_EMAIL = "ana.souza@vanep.com";
  private static final Long CLIENT_ID = 100L;

  @Mock private DependentRepository dependents;
  @Mock private ClientRepository clients;
  @Mock private UserRepository users;
  @Mock private DependentMapper mapper;

  private DependentService service;

  @BeforeEach
  void setUp() {
    service = new DependentService(dependents, clients, users, mapper);
  }

  private Jwt clientJwt() {
    Jwt jwt = mock(Jwt.class);
    lenient().when(jwt.getSubject()).thenReturn(CLIENT_EMAIL);
    lenient().when(jwt.getClaim("roles")).thenReturn(List.of("ROLE_CLIENT"));
    return jwt;
  }

  private void stubOwnershipResolution() {
    UserModel user = new UserModel();
    user.setId(10L);
    user.setEmail(CLIENT_EMAIL);
    ClientModel client = new ClientModel();
    client.setId(CLIENT_ID);
    lenient().when(users.findByEmail(CLIENT_EMAIL)).thenReturn(Optional.of(user));
    lenient().when(clients.findByUserId(10L)).thenReturn(Optional.of(client));
  }

  private void stubResponseMapping() {
    ClientModel client = new ClientModel();
    client.setId(CLIENT_ID);
    client.setToken("ctok");
    lenient().when(clients.findById(CLIENT_ID)).thenReturn(Optional.of(client));
    lenient()
        .when(mapper.toResponse(any(DependentModel.class), eq("ctok"), isNull(), isNull()))
        .thenReturn(response());
  }

  private DependentResponseDTO response() {
    return new DependentResponseDTO(
        "tok",
        null,
        "Kid",
        null,
        null,
        null,
        null,
        null,
        false,
        false,
        Shift.MORNING,
        null,
        null,
        null,
        null);
  }

  private DependentModel dependent(boolean isDefault) {
    DependentModel model = new DependentModel();
    model.setClientId(CLIENT_ID);
    model.setName("Kid");
    model.setShift(Shift.MORNING);
    model.setDefaultDependent(isDefault);
    return model;
  }

  @Test
  void firstDependentBecomesDefault() {
    DependentCreateDTO dto = new DependentCreateDTO();
    dto.setName("Kid");
    DependentModel model = dependent(false);

    stubOwnershipResolution();
    stubResponseMapping();
    when(mapper.toModel(dto, CLIENT_ID, null, null)).thenReturn(model);
    when(dependents.countByClientId(CLIENT_ID)).thenReturn(0L);
    when(dependents.save(model)).thenReturn(model);

    service.create(clientJwt(), dto);

    assertThat(model.isDefaultDependent()).isTrue();
  }

  @Test
  void additionalDependentIsNotDefaultByDefault() {
    DependentCreateDTO dto = new DependentCreateDTO();
    dto.setName("Kid");
    DependentModel model = dependent(false);

    stubOwnershipResolution();
    stubResponseMapping();
    when(mapper.toModel(dto, CLIENT_ID, null, null)).thenReturn(model);
    when(dependents.countByClientId(CLIENT_ID)).thenReturn(2L);
    when(dependents.save(model)).thenReturn(model);

    service.create(clientJwt(), dto);

    assertThat(model.isDefaultDependent()).isFalse();
  }

  @Test
  void settingDefaultOnCreateUnsetsOthers() {
    DependentCreateDTO dto = new DependentCreateDTO();
    dto.setName("Kid");
    dto.setIsDefault(true);
    DependentModel model = dependent(false);
    DependentModel previousDefault = dependent(true);
    previousDefault.setToken("old");

    stubOwnershipResolution();
    stubResponseMapping();
    when(mapper.toModel(dto, CLIENT_ID, null, null)).thenReturn(model);
    when(dependents.countByClientId(CLIENT_ID)).thenReturn(1L);
    when(dependents.findByClientId(CLIENT_ID)).thenReturn(List.of(previousDefault));
    when(dependents.save(any(DependentModel.class))).thenReturn(model);

    service.create(clientJwt(), dto);

    assertThat(model.isDefaultDependent()).isTrue();
    assertThat(previousDefault.isDefaultDependent()).isFalse();
  }

  @Test
  void deletingDefaultPromotesRemainingWhenExactlyOneLeft() {
    DependentModel toDelete = dependent(true);
    toDelete.setToken("tok");
    DependentModel remaining = dependent(false);
    remaining.setToken("other");

    stubOwnershipResolution();
    when(dependents.findByToken("tok")).thenReturn(Optional.of(toDelete));
    when(dependents.findByClientId(CLIENT_ID)).thenReturn(List.of(remaining));

    service.delete(clientJwt(), "tok");

    verify(dependents).delete(toDelete);
    assertThat(remaining.isDefaultDependent()).isTrue();
  }

  @Test
  void deletingDefaultDoesNotPromoteWhenMultipleRemain() {
    DependentModel toDelete = dependent(true);
    toDelete.setToken("tok");
    DependentModel first = dependent(false);
    DependentModel second = dependent(false);

    stubOwnershipResolution();
    when(dependents.findByToken("tok")).thenReturn(Optional.of(toDelete));
    when(dependents.findByClientId(CLIENT_ID)).thenReturn(List.of(first, second));

    service.delete(clientJwt(), "tok");

    verify(dependents).delete(toDelete);
    verify(dependents, never()).save(any());
    assertThat(first.isDefaultDependent()).isFalse();
    assertThat(second.isDefaultDependent()).isFalse();
  }
}
