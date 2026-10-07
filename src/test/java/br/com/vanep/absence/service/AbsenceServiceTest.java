package br.com.vanep.absence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.absence.enums.AbsenceLeg;
import br.com.vanep.absence.enums.AbsenceScope;
import br.com.vanep.absence.enums.AbsenceSource;
import br.com.vanep.absence.model.AbsenceModel;
import br.com.vanep.absence.repository.AbsenceRepository;
import br.com.vanep.assistant.enums.AssistantStatus;
import br.com.vanep.assistant.model.AssistantModel;
import br.com.vanep.assistant.repository.AssistantRepository;
import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.clientdriver.enums.RelationshipStatus;
import br.com.vanep.clientdriver.model.ClientDriverModel;
import br.com.vanep.clientdriver.repository.ClientDriverRepository;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.dependent.repository.DependentRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.shared.enums.Shift;
import br.com.vanep.trip.enums.TripStatus;
import br.com.vanep.trip.model.TripModel;
import br.com.vanep.trip.repository.TripRepository;
import br.com.vanep.trip.service.TripService;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.service.UserService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AbsenceServiceTest {

  private static final String CLIENT_UID = "client-uid";
  private static final String DRIVER_UID = "driver-uid";
  private static final String ASSISTANT_UID = "assistant-uid";
  private static final String LINK = "link-token";
  private static final String DEPENDENT = "dep-token";
  private static final String TRIP = "trip-token";

  @Mock private AbsenceRepository absences;
  @Mock private ClientDriverRepository links;
  @Mock private DependentRepository dependents;
  @Mock private ClientRepository clients;
  @Mock private TripRepository trips;
  @Mock private AssistantRepository assistants;
  @Mock private UserService users;
  @Mock private AbsenceDayEffectPort effects;
  @Mock private AbsenceCounterpartNotifier notifier;
  @Mock private MessageSource messages;

  private AbsenceService service;
  private ClientModel client;
  private DriverModel driver;
  private ClientDriverModel link;
  private DependentModel dependent;
  private UserModel clientUser;
  private UserModel driverUser;

  @BeforeEach
  void setUp() {
    AbsenceLegShiftPolicy legShift = new AbsenceLegShiftPolicy();
    service =
        new AbsenceService(
            absences,
            links,
            dependents,
            clients,
            trips,
            assistants,
            users,
            legShift,
            new AbsenceScopePolicy(legShift),
            new AbsenceUndoPolicy(),
            effects,
            notifier,
            messages);

    clientUser = user(1L, CLIENT_UID, UserType.CLIENT);
    driverUser = user(2L, DRIVER_UID, UserType.DRIVER);
    client = new ClientModel();
    client.setId(10L);
    client.setUser(clientUser);
    driver = new DriverModel();
    driver.setId(20L);
    driver.setUser(driverUser);
    link = new ClientDriverModel();
    link.setId(30L);
    link.setToken(LINK);
    link.setClient(client);
    link.setDriver(driver);
    link.setStatus(RelationshipStatus.ACTIVE);
    dependent = new DependentModel();
    dependent.setId(40L);
    dependent.setToken(DEPENDENT);
    dependent.setClientId(10L);
    dependent.setShift(Shift.FULLTIME);

    when(users.requireByTokenAndType(CLIENT_UID, UserType.CLIENT)).thenReturn(clientUser);
    when(users.requireByToken(DRIVER_UID)).thenReturn(driverUser);
    when(clients.findByUserId(1L)).thenReturn(Optional.of(client));
    when(links.findByToken(LINK)).thenReturn(Optional.of(link));
    when(dependents.findByToken(DEPENDENT)).thenReturn(Optional.of(dependent));
    when(trips.findByDriverAndServiceDate(eq(20L), any(LocalDate.class))).thenReturn(List.of());
    when(messages.getMessage(anyString(), any(), any())).thenReturn("mensagem");
    when(absences.saveAndFlush(any(AbsenceModel.class)))
        .thenAnswer(
            invocation -> {
              AbsenceModel row = invocation.getArgument(0);
              if (row.getToken() == null) {
                row.setToken("abs-" + row.getLeg());
              }
              return row;
            });
  }

  @Test
  void bothCreatesTwoRows() {
    AbsenceMutationResult result =
        service.reportForClient(CLIENT_UID, LINK, DEPENDENT, AbsenceScope.BOTH);

    assertThat(result.createdAny()).isTrue();
    assertThat(result.absences()).hasSize(2);
    assertThat(result.absences())
        .extracting(AbsenceModel::getLeg)
        .containsExactly(AbsenceLeg.OUTBOUND, AbsenceLeg.RETURN);
    verify(effects, times(2)).onAbsenceRecorded(any());
    verify(notifier, times(2)).notifyCounterpart(any());
  }

  @Test
  void secondOutboundRereadsTheWinner() {
    AbsenceModel winner = stored(AbsenceLeg.OUTBOUND);
    when(absences.saveAndFlush(any(AbsenceModel.class)))
        .thenThrow(new DataIntegrityViolationException("dup"));
    when(absences.findByDependentAndDateAndLeg(
            eq(40L), any(LocalDate.class), eq(AbsenceLeg.OUTBOUND)))
        .thenReturn(Optional.of(winner));

    AbsenceMutationResult result =
        service.reportForClient(CLIENT_UID, LINK, DEPENDENT, AbsenceScope.OUTBOUND);

    assertThat(result.createdAny()).isFalse();
    assertThat(result.absences()).containsExactly(winner);
  }

  @Test
  void bothWithExistingOutboundOnlyInsertsReturn() {
    AbsenceModel existing = stored(AbsenceLeg.OUTBOUND);
    when(absences.findByDependentAndDateAndLeg(
            eq(40L), any(LocalDate.class), eq(AbsenceLeg.OUTBOUND)))
        .thenReturn(Optional.of(existing));
    when(absences.findByDependentAndDateAndLeg(
            eq(40L), any(LocalDate.class), eq(AbsenceLeg.RETURN)))
        .thenReturn(Optional.empty());

    AbsenceMutationResult result =
        service.reportForClient(CLIENT_UID, LINK, DEPENDENT, AbsenceScope.BOTH);

    assertThat(result.createdAny()).isTrue();
    assertThat(result.absences()).hasSize(2);
    assertThat(result.absences().get(0)).isSameAs(existing);
    assertThat(result.absences().get(1).getLeg()).isEqualTo(AbsenceLeg.RETURN);
  }

  @Test
  void anotherClientsDependentIsForbidden() {
    dependent.setClientId(99L);

    assertThatThrownBy(
            () -> service.reportForClient(CLIENT_UID, LINK, DEPENDENT, AbsenceScope.OUTBOUND))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    verify(absences, never()).saveAndFlush(any());
  }

  @Test
  void inactiveLinkIsConflict() {
    link.setStatus(RelationshipStatus.INACTIVE);

    assertThatThrownBy(
            () -> service.reportForClient(CLIENT_UID, LINK, DEPENDENT, AbsenceScope.OUTBOUND))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void reportingDoesNotWriteTheDependent() {
    service.reportForClient(CLIENT_UID, LINK, DEPENDENT, AbsenceScope.OUTBOUND);

    verify(dependents, never()).save(any());
    verify(links, never()).save(any());
  }

  @Test
  void bothIsRejectedWhenTheDependentIsMorningOnly() {
    dependent.setShift(Shift.MORNING);

    assertThatThrownBy(
            () -> service.reportForClient(CLIENT_UID, LINK, DEPENDENT, AbsenceScope.BOTH))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void noShowStoresReasonAndTrip() {
    TripModel trip = morningTrip(TripStatus.IN_PROGRESS);
    when(trips.findByToken(TRIP)).thenReturn(Optional.of(trip));
    when(links.findByPair(10L, 20L)).thenReturn(Optional.of(link));

    AbsenceMutationResult result = service.reportNoShow(DRIVER_UID, TRIP, DEPENDENT, "not at stop");

    ArgumentCaptor<AbsenceModel> captor = ArgumentCaptor.forClass(AbsenceModel.class);
    verify(absences).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getReason()).isEqualTo("not at stop");
    assertThat(captor.getValue().getTrip()).isSameAs(trip);
    assertThat(captor.getValue().getSource()).isEqualTo(AbsenceSource.DRIVER);
    assertThat(captor.getValue().getLeg()).isEqualTo(AbsenceLeg.OUTBOUND);
    assertThat(result.createdAny()).isTrue();
  }

  @Test
  void assistantNoShowUsesAssistantSource() {
    TripModel trip = morningTrip(TripStatus.IN_PROGRESS);
    UserModel assistantUser = user(3L, ASSISTANT_UID, UserType.ASSISTANT);
    AssistantModel assistant = new AssistantModel();
    assistant.setStatus(AssistantStatus.ACTIVE);
    assistant.setDriver(driver);
    when(trips.findByToken(TRIP)).thenReturn(Optional.of(trip));
    when(users.requireByToken(ASSISTANT_UID)).thenReturn(assistantUser);
    when(assistants.findByUserId(3L)).thenReturn(Optional.of(assistant));
    when(links.findByPair(10L, 20L)).thenReturn(Optional.of(link));

    AbsenceMutationResult result =
        service.reportNoShow(ASSISTANT_UID, TRIP, DEPENDENT, "waited at the gate");

    assertThat(result.absences().get(0).getSource()).isEqualTo(AbsenceSource.ASSISTANT);
  }

  @Test
  void blankReasonIsRejected() {
    assertThatThrownBy(() -> service.reportNoShow(DRIVER_UID, TRIP, DEPENDENT, "  "))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    verify(absences, never()).saveAndFlush(any());
  }

  @Test
  void completedTripRejectsNoShow() {
    TripModel trip = morningTrip(TripStatus.COMPLETED);
    when(trips.findByToken(TRIP)).thenReturn(Optional.of(trip));

    assertThatThrownBy(() -> service.reportNoShow(DRIVER_UID, TRIP, DEPENDENT, "not at stop"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void undoSoftDeletesClientRows() {
    AbsenceModel row = stored(AbsenceLeg.OUTBOUND);
    when(absences.findByDependentAndDateAndLeg(
            eq(40L), any(LocalDate.class), eq(AbsenceLeg.OUTBOUND)))
        .thenReturn(Optional.of(row));

    service.undoForClient(CLIENT_UID, LINK, DEPENDENT, AbsenceScope.OUTBOUND);

    verify(absences).delete(row);
  }

  @Test
  void undoIsRefusedWhenTheTripIsInProgress() {
    AbsenceModel row = stored(AbsenceLeg.OUTBOUND);
    TripModel trip = morningTrip(TripStatus.IN_PROGRESS);
    row.setTrip(trip);
    when(absences.findByDependentAndDateAndLeg(
            eq(40L), any(LocalDate.class), eq(AbsenceLeg.OUTBOUND)))
        .thenReturn(Optional.of(row));

    assertThatThrownBy(
            () -> service.undoForClient(CLIENT_UID, LINK, DEPENDENT, AbsenceScope.OUTBOUND))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    verify(absences, never()).delete(any());
  }

  @Test
  void losingTheInsertRaceDoesNotReturn500() {
    AbsenceModel winner = stored(AbsenceLeg.OUTBOUND);
    when(absences.saveAndFlush(any(AbsenceModel.class)))
        .thenThrow(new DataIntegrityViolationException("dup"));
    when(absences.findByDependentAndDateAndLeg(
            eq(40L), any(LocalDate.class), eq(AbsenceLeg.OUTBOUND)))
        .thenReturn(Optional.empty(), Optional.of(winner));

    AbsenceMutationResult result =
        service.reportForClient(CLIENT_UID, LINK, DEPENDENT, AbsenceScope.OUTBOUND);

    assertThat(result.absences()).containsExactly(winner);
  }

  @Test
  void listTodayReturnsTheStoredRows() {
    AbsenceModel row = stored(AbsenceLeg.OUTBOUND);
    when(absences.findByDependentAndDate(eq(40L), any(LocalDate.class))).thenReturn(List.of(row));

    assertThat(service.listTodayForClient(CLIENT_UID, LINK, DEPENDENT)).containsExactly(row);
  }

  private AbsenceModel stored(AbsenceLeg leg) {
    AbsenceModel row = new AbsenceModel();
    row.setToken("abs-" + leg);
    row.setLeg(leg);
    row.setSource(AbsenceSource.CLIENT);
    row.setDependent(dependent);
    row.setClientDriver(link);
    row.setAbsenceDate(LocalDate.now(TripService.SERVICE_ZONE));
    return row;
  }

  private TripModel morningTrip(TripStatus status) {
    TripModel trip = new TripModel();
    trip.setToken(TRIP);
    trip.setDriver(driver);
    trip.setShift(Shift.MORNING);
    trip.setStatus(status);
    trip.setServiceDate(LocalDate.now(TripService.SERVICE_ZONE));
    return trip;
  }

  private static UserModel user(Long id, String token, UserType type) {
    UserModel user = new UserModel();
    user.setId(id);
    user.setToken(token);
    user.setType(type);
    return user;
  }
}
