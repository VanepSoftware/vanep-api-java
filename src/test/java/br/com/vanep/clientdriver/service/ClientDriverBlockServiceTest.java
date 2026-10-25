package br.com.vanep.clientdriver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.vanep.client.model.ClientModel;
import br.com.vanep.clientdriver.dto.ClientDriverResponseDTO;
import br.com.vanep.clientdriver.enums.LinkOrigin;
import br.com.vanep.clientdriver.enums.LinkParty;
import br.com.vanep.clientdriver.enums.RelationshipStatus;
import br.com.vanep.clientdriver.mapper.ClientDriverMapper;
import br.com.vanep.clientdriver.model.ClientDriverModel;
import br.com.vanep.clientdriver.repository.ClientDriverRepository;
import br.com.vanep.contract.enums.ContractStatus;
import br.com.vanep.contract.repository.ContractRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.proposal.enums.ProposalStatus;
import br.com.vanep.servicerequest.enums.ServiceRequestStatus;
import br.com.vanep.servicerequest.service.ServiceRequestService;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.service.UserService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClientDriverBlockServiceTest {

  private static final String MARIA_UID = "maria-uid";
  private static final String CARLOS_UID = "carlos-uid";

  @Mock private ClientDriverRepository links;
  @Mock private ContractRepository contracts;
  @Mock private ServiceRequestService requests;
  @Mock private UserService users;
  @Mock private MessageSource messages;

  private ClientDriverBlockService service;
  private UserModel mariaUser;
  private UserModel carlosUser;
  private ClientDriverModel link;

  @BeforeEach
  void setUp() {
    service =
        new ClientDriverBlockService(
            links,
            contracts,
            requests,
            users,
            new LinkStatusPolicy(),
            new ClientDriverMapper(),
            messages);
    when(messages.getMessage(anyString(), any(), any())).thenAnswer(call -> call.getArgument(0));

    mariaUser = user(1L, "Maria", UserType.CLIENT);
    carlosUser = user(2L, "Carlos", UserType.DRIVER);
    when(users.requireByToken(MARIA_UID)).thenReturn(mariaUser);
    when(users.requireByToken(CARLOS_UID)).thenReturn(carlosUser);
    ClientModel maria = new ClientModel();
    maria.setToken("maria");
    maria.setUser(mariaUser);
    DriverModel carlos = new DriverModel();
    carlos.setToken("carlos");
    carlos.setUser(carlosUser);
    link = new ClientDriverModel();
    link.setId(40L);
    link.setToken("link");
    link.setClient(maria);
    link.setDriver(carlos);
    link.setStatus(RelationshipStatus.PENDING);
    link.setOrigin(LinkOrigin.CLIENT_SEARCH);
    when(links.findByToken("link")).thenReturn(Optional.of(link));
    when(contracts.findStatusesByClientDriverId(40L)).thenReturn(List.of());
    when(links.block(eq(40L), any(), any())).thenReturn(1);
    when(links.unblock(eq(40L), any(), any())).thenReturn(1);
  }

  @Test
  void theClientBlocksAndWithdrawsTheNegotiation() {
    when(links.findByToken("link"))
        .thenReturn(Optional.of(link))
        .thenReturn(Optional.of(blockedBy(mariaUser)));

    ClientDriverResponseDTO response = service.block(MARIA_UID, "link");

    assertThat(response.status()).isEqualTo(RelationshipStatus.BLOCKED);
    assertThat(response.blockedBy()).isEqualTo(LinkParty.CLIENT);
    verify(links).block(eq(40L), eq(1L), any(Instant.class));
    verify(requests)
        .closeOpenRequestOf(link, ServiceRequestStatus.WITHDRAWN, ProposalStatus.DECLINED);
  }

  @Test
  void theDriverBlocksAndDeclinesTheNegotiation() {
    when(links.findByToken("link"))
        .thenReturn(Optional.of(link))
        .thenReturn(Optional.of(blockedBy(carlosUser)));

    ClientDriverResponseDTO response = service.block(CARLOS_UID, "link");

    assertThat(response.blockedBy()).isEqualTo(LinkParty.DRIVER);
    verify(links).block(eq(40L), eq(2L), any(Instant.class));
    verify(requests)
        .closeOpenRequestOf(link, ServiceRequestStatus.DECLINED, ProposalStatus.WITHDRAWN);
  }

  @ParameterizedTest
  @EnumSource(
      value = ContractStatus.class,
      names = {"SIGNED", "ACTIVE", "SUSPENDED"})
  void aContractInProgressPreventsTheBlock(ContractStatus status) {
    when(contracts.findStatusesByClientDriverId(40L))
        .thenReturn(List.of(ContractStatus.ENDED, status));

    assertRefused(
        () -> service.block(MARIA_UID, "link"),
        HttpStatus.UNPROCESSABLE_CONTENT,
        "client_driver.block.has_contract");
    verify(links, never()).block(any(), any(), any());
    verifyNoInteractions(requests);
  }

  @Test
  void contractsThatEndedDoNotPreventTheBlock() {
    when(contracts.findStatusesByClientDriverId(40L))
        .thenReturn(List.of(ContractStatus.ENDED, ContractStatus.CANCELLED));

    service.block(MARIA_UID, "link");

    verify(links).block(eq(40L), eq(1L), any(Instant.class));
  }

  @Test
  void anAlreadyBlockedLinkIsAConflict() {
    when(links.findByToken("link")).thenReturn(Optional.of(blockedBy(carlosUser)));

    assertRefused(
        () -> service.block(MARIA_UID, "link"),
        HttpStatus.CONFLICT,
        "client_driver.already_blocked");
    verify(links, never()).block(any(), any(), any());
    verifyNoInteractions(requests);
  }

  @Test
  void losingTheRaceToBlockIsAConflictAndClosesNothing() {
    when(links.block(eq(40L), any(), any())).thenReturn(0);

    assertRefused(
        () -> service.block(CARLOS_UID, "link"),
        HttpStatus.CONFLICT,
        "client_driver.already_blocked");
    verifyNoInteractions(requests);
  }

  @Test
  void theBlockerUnblocksToPendingWhenThereIsNoContract() {
    when(links.findByToken("link"))
        .thenReturn(Optional.of(blockedBy(carlosUser)))
        .thenReturn(Optional.of(link));

    ClientDriverResponseDTO response = service.unblock(CARLOS_UID, "link");

    verify(links).unblock(40L, 2L, "PENDING");
    assertThat(response.status()).isEqualTo(RelationshipStatus.PENDING);
    assertThat(response.blockedBy()).isNull();
  }

  @Test
  void theBlockerUnblocksToInactiveAfterAnEndedContract() {
    when(links.findByToken("link")).thenReturn(Optional.of(blockedBy(mariaUser)));
    when(contracts.findStatusesByClientDriverId(40L)).thenReturn(List.of(ContractStatus.ENDED));

    service.unblock(MARIA_UID, "link");

    verify(links).unblock(40L, 1L, "INACTIVE");
  }

  @Test
  void theBlockedPartyCannotUnblock() {
    when(links.findByToken("link")).thenReturn(Optional.of(blockedBy(carlosUser)));

    assertRefused(
        () -> service.unblock(MARIA_UID, "link"),
        HttpStatus.FORBIDDEN,
        "client_driver.unblock.not_blocker");
    verify(links, never()).unblock(any(), any(), any());
  }

  @Test
  void unblockingALinkThatIsNotBlockedIsAConflict() {
    assertRefused(
        () -> service.unblock(MARIA_UID, "link"), HttpStatus.CONFLICT, "client_driver.not_blocked");
    verify(links, never()).unblock(any(), any(), any());
  }

  @Test
  void losingTheRaceToUnblockIsAConflict() {
    when(links.findByToken("link")).thenReturn(Optional.of(blockedBy(carlosUser)));
    when(links.unblock(eq(40L), any(), any())).thenReturn(0);

    assertRefused(
        () -> service.unblock(CARLOS_UID, "link"),
        HttpStatus.CONFLICT,
        "client_driver.not_blocked");
  }

  @Test
  void anUnknownLinkIsNotFound() {
    when(links.findByToken("unknown")).thenReturn(Optional.empty());

    assertRefused(
        () -> service.block(MARIA_UID, "unknown"), HttpStatus.NOT_FOUND, "client_driver.not_found");
    assertRefused(
        () -> service.unblock(MARIA_UID, "unknown"),
        HttpStatus.NOT_FOUND,
        "client_driver.not_found");
  }

  private ClientDriverModel blockedBy(UserModel blocker) {
    ClientDriverModel blocked = new ClientDriverModel();
    blocked.setId(link.getId());
    blocked.setToken(link.getToken());
    blocked.setClient(link.getClient());
    blocked.setDriver(link.getDriver());
    blocked.setOrigin(link.getOrigin());
    blocked.setStatus(RelationshipStatus.BLOCKED);
    blocked.setBlockedBy(blocker);
    blocked.setBlockedAt(Instant.now());
    return blocked;
  }

  private void assertRefused(Runnable call, HttpStatus status, String key) {
    assertThatThrownBy(() -> call.run())
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> {
              assertThat(e.getStatusCode()).isEqualTo(status);
              assertThat(e.getReason()).isEqualTo(key);
            });
  }

  private UserModel user(Long id, String name, UserType type) {
    UserModel user = new UserModel();
    user.setId(id);
    user.setName(name);
    user.setType(type);
    return user;
  }
}
