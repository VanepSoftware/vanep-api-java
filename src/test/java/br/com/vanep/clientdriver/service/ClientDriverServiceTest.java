package br.com.vanep.clientdriver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.clientdriver.dto.ClientDriverCreateRequestDTO;
import br.com.vanep.clientdriver.dto.ClientDriverResponseDTO;
import br.com.vanep.clientdriver.enums.RelationshipStatus;
import br.com.vanep.clientdriver.mapper.ClientDriverMapper;
import br.com.vanep.clientdriver.model.ClientDriverModel;
import br.com.vanep.clientdriver.repository.ClientDriverRepository;
import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.service.UserService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClientDriverServiceTest {

  @Mock private ClientDriverRepository links;
  @Mock private ClientRepository clients;
  @Mock private DriverRepository drivers;
  @Mock private UserService users;
  @Mock private ClientDriverMapper mapper;
  @Mock private MessageSource messages;

  @InjectMocks private ClientDriverService service;

  @Captor private ArgumentCaptor<ClientDriverModel> saved;

  private ClientModel maria;
  private DriverModel carlos;

  @BeforeEach
  void setUp() {
    when(messages.getMessage(anyString(), any(), any())).thenAnswer(call -> call.getArgument(0));

    maria = new ClientModel();
    maria.setId(1L);
    maria.setUser(user(10L, "maria-uid", UserType.CLIENT));

    carlos = new DriverModel();
    carlos.setId(2L);
    carlos.setUser(user(20L, "carlos-uid", UserType.DRIVER));
    carlos.setApprovalStatus(DriverApprovalStatus.APPROVED);

    when(clients.findByToken("cli")).thenReturn(Optional.of(maria));
    when(drivers.findByToken("drv")).thenReturn(Optional.of(carlos));
    when(links.findByPair(anyLong(), anyLong())).thenReturn(Optional.empty());
    when(links.save(any())).thenAnswer(call -> call.getArgument(0));
    when(mapper.toResponse(any())).thenAnswer(call -> response(call.getArgument(0)));
  }

  @Test
  void createsTheLinkAsPending() {
    service.create(new ClientDriverCreateRequestDTO("cli", "drv"));

    verify(links).save(saved.capture());
    assertThat(saved.getValue().getStatus()).isEqualTo(RelationshipStatus.PENDING);
    assertThat(saved.getValue().getClient()).isSameAs(maria);
    assertThat(saved.getValue().getDriver()).isSameAs(carlos);
  }

  @Test
  void refusesADuplicatePair() {
    when(links.findByPair(1L, 2L)).thenReturn(Optional.of(link(RelationshipStatus.ACTIVE)));

    assertThatThrownBy(() -> service.create(new ClientDriverCreateRequestDTO("cli", "drv")))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("409")
        .hasMessageContaining("client_driver.duplicate_pair");

    verify(links, never()).save(any());
  }

  @Test
  void refusesAnUnknownClient() {
    when(clients.findByToken("ghost")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.create(new ClientDriverCreateRequestDTO("ghost", "drv")))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404")
        .hasMessageContaining("client_driver.client.not_found");
  }

  @Test
  void refusesAnUnknownDriver() {
    when(drivers.findByToken("ghost")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.create(new ClientDriverCreateRequestDTO("cli", "ghost")))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404")
        .hasMessageContaining("client_driver.driver.not_found");
  }

  @Test
  void refusesCreateWhenDriverIsNotApproved() {
    for (DriverApprovalStatus unapprovedStatus :
        List.of(
            DriverApprovalStatus.PENDING,
            DriverApprovalStatus.UNDER_REVIEW,
            DriverApprovalStatus.REJECTED)) {
      carlos.setApprovalStatus(unapprovedStatus);

      assertThatThrownBy(() -> service.create(new ClientDriverCreateRequestDTO("cli", "drv")))
          .isInstanceOf(ResponseStatusException.class)
          .satisfies(
              e -> {
                ResponseStatusException ex = (ResponseStatusException) e;
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                assertThat(ex.getReason()).isEqualTo("client_driver.driver.not_approved");
              });
    }

    carlos.setApprovalStatus(null);
    assertThatThrownBy(() -> service.create(new ClientDriverCreateRequestDTO("cli", "drv")))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            e -> {
              ResponseStatusException ex = (ResponseStatusException) e;
              assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(ex.getReason()).isEqualTo("client_driver.driver.not_approved");
            });

    verify(links, never()).save(any());
  }

  @Test
  void restoringALinkThatWasNeverRemovedIsRefused() {
    when(links.existsDeletedByToken("lnk")).thenReturn(false);

    assertThatThrownBy(() -> service.restore("lnk"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");

    verify(links, never()).restoreByToken(anyString());
  }

  @Test
  void restoringBringsTheLinkBack() {
    when(links.existsDeletedByToken("lnk")).thenReturn(true);
    when(links.findByToken("lnk")).thenReturn(Optional.of(link(RelationshipStatus.ACTIVE)));

    ClientDriverResponseDTO restored = service.restore("lnk");

    verify(links).restoreByToken("lnk");
    assertThat(restored.status()).isEqualTo(RelationshipStatus.ACTIVE);
  }

  @Test
  void aClientCallerIsMatchedOnTheClientSide() {
    when(users.requireByToken("maria-uid")).thenReturn(maria.getUser());
    when(links.findByClientUserId(10L)).thenReturn(List.of(link(RelationshipStatus.ACTIVE)));

    assertThat(service.findMine("maria-uid")).hasSize(1);

    verify(links).findByClientUserId(10L);
    verify(links, never()).findByDriverUserId(anyLong());
  }

  @Test
  void aDriverCallerIsMatchedOnTheDriverSide() {
    when(users.requireByToken("carlos-uid")).thenReturn(carlos.getUser());
    when(links.findByDriverUserId(20L)).thenReturn(List.of(link(RelationshipStatus.ACTIVE)));

    assertThat(service.findMine("carlos-uid")).hasSize(1);

    verify(links).findByDriverUserId(20L);
    verify(links, never()).findByClientUserId(anyLong());
  }

  @Test
  void aCallerWithNoLinksGetsAnEmptyList() {
    when(users.requireByToken("maria-uid")).thenReturn(maria.getUser());
    when(links.findByClientUserId(10L)).thenReturn(List.of());

    assertThat(service.findMine("maria-uid")).isEmpty();
  }

  @Test
  void deletingAnUnknownLinkIsRefused() {
    when(links.findByToken("ghost")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.delete("ghost"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404")
        .hasMessageContaining("client_driver.not_found");
  }

  private UserModel user(Long id, String token, UserType type) {
    UserModel user = new UserModel();
    user.setId(id);
    user.setToken(token);
    user.setType(type);
    user.setName("Fulano");
    return user;
  }

  private ClientDriverModel link(RelationshipStatus status) {
    ClientDriverModel link = new ClientDriverModel();
    link.setClient(maria);
    link.setDriver(carlos);
    link.setStatus(status);
    return link;
  }

  private ClientDriverResponseDTO response(ClientDriverModel link) {
    return new ClientDriverResponseDTO(
        link.getToken(),
        link.getClient().getToken(),
        link.getClient().getUser().getName(),
        link.getDriver().getToken(),
        link.getDriver().getUser().getName(),
        link.getStatus(),
        link.getCreatedAt(),
        link.getUpdatedAt());
  }
}
