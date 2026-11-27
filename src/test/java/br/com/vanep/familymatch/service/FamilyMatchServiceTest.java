package br.com.vanep.familymatch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.vanep.contract.repository.ContractItemRepository;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.dependent.repository.DependentRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.familyinvite.model.FamilyInviteModel;
import br.com.vanep.familymatch.enums.FamilyMatchStatus;
import br.com.vanep.familymatch.model.FamilyMatchModel;
import br.com.vanep.familymatch.repository.FamilyMatchRepository;
import br.com.vanep.shared.error.ApiProblemException;
import br.com.vanep.unlinkedpassenger.dto.UnlinkedPassengerResponseDTO;
import br.com.vanep.unlinkedpassenger.model.UnlinkedPassengerModel;
import br.com.vanep.unlinkedpassenger.repository.UnlinkedPassengerRepository;
import br.com.vanep.unlinkedpassenger.service.UnlinkedPassengerService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class FamilyMatchServiceTest {

  @Mock private FamilyMatchRepository matches;
  @Mock private UnlinkedPassengerRepository passengers;
  @Mock private UnlinkedPassengerService passengerService;
  @Mock private DependentRepository dependents;
  @Mock private ContractItemRepository contractItems;

  private FamilyMatchService service;

  @BeforeEach
  void setUp() {
    service =
        new FamilyMatchService(
            matches,
            passengers,
            passengerService,
            dependents,
            contractItems,
            new FamilyMatchCandidatePolicy());
  }

  @Test
  void suggestMatchSkipsGroupInviteWithoutSchoolOrConflictingPendingMatch() {
    FamilyInviteModel groupInvite = new FamilyInviteModel();
    groupInvite.setId(1L);
    groupInvite.setDriverId(5L);

    DependentModel noSchool = dependent(10L, null);
    assertThat(service.suggestMatch(groupInvite, noSchool)).isEmpty();

    UnlinkedPassengerModel passenger = passenger(30L, 5L, "Ana");
    FamilyMatchModel otherPending = match(40L, 30L, 999L);
    when(matches.findPendingByUnlinkedPassengerId(30L)).thenReturn(Optional.of(otherPending));
    assertThat(service.findOrCreatePendingMatch(groupInvite, passenger, dependent(10L, 7L)))
        .isEmpty();
  }

  @Test
  void suggestMatchRejectsAlreadyLinkedPassengerAndConflictingPendingOnDirectInvite() {
    FamilyInviteModel directInvite = new FamilyInviteModel();
    directInvite.setId(2L);
    directInvite.setDriverId(5L);
    directInvite.setUnlinkedPassengerId(30L);

    DependentModel dependent = dependent(10L, 7L);
    UnlinkedPassengerModel linked = passenger(30L, 5L, "Ana");
    linked.setDependentId(77L);
    when(passengers.findById(30L)).thenReturn(Optional.of(linked));

    assertThatThrownBy(() -> service.suggestMatch(directInvite, dependent))
        .isInstanceOf(ApiProblemException.class)
        .satisfies(
            ex -> {
              ApiProblemException problem = (ApiProblemException) ex;
              assertThat(problem.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(problem.getCode()).isEqualTo("family_invite.already_linked");
            });

    UnlinkedPassengerModel available = passenger(31L, 5L, "Bia");
    directInvite.setUnlinkedPassengerId(31L);
    FamilyMatchModel otherPending = match(41L, 31L, 999L);
    when(passengers.findById(31L)).thenReturn(Optional.of(available));
    when(matches.findPendingByUnlinkedPassengerId(31L)).thenReturn(Optional.of(otherPending));

    assertThatThrownBy(() -> service.suggestMatch(directInvite, dependent))
        .isInstanceOf(ApiProblemException.class)
        .satisfies(
            ex -> {
              ApiProblemException problem = (ApiProblemException) ex;
              assertThat(problem.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(problem.getCode()).isEqualTo("family_match.already_pending");
            });
  }

  @Test
  void mergeRejectsAlreadyLinkedDependentAndStaleRespondTransition() {
    UnlinkedPassengerModel passenger = passenger(30L, 5L, "Ana");
    FamilyMatchModel pending = match(40L, 30L, 10L);
    DependentModel dependent = dependent(10L, 7L);
    dependent.setName("Ana Silva");

    when(passengerService.requireOwnPassenger("u-1", "p-1")).thenReturn(passenger);
    when(matches.findPendingByUnlinkedPassengerId(30L)).thenReturn(Optional.of(pending));
    when(dependents.findById(10L)).thenReturn(Optional.of(dependent));
    when(passengers.existsByDriver_IdAndDependentIdAndIdNot(5L, 10L, 30L)).thenReturn(true);

    assertThatThrownBy(() -> service.merge("u-1", "p-1"))
        .isInstanceOf(ApiProblemException.class)
        .satisfies(
            ex -> {
              ApiProblemException problem = (ApiProblemException) ex;
              assertThat(problem.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(problem.getCode()).isEqualTo("family_match.dependent_already_linked");
            });

    when(matches.respondIfPending(eq(40L), eq(FamilyMatchStatus.DISMISSED.name()), any()))
        .thenReturn(0);

    assertThatThrownBy(() -> service.dismiss("u-1", "p-1"))
        .isInstanceOf(ApiProblemException.class)
        .satisfies(
            ex ->
                assertThat(((ApiProblemException) ex).getCode())
                    .isEqualTo("family_match.not_pending"));

    UnlinkedPassengerResponseDTO dto = mock(UnlinkedPassengerResponseDTO.class);
    when(matches.respondIfPending(eq(40L), eq(FamilyMatchStatus.DISMISSED.name()), any()))
        .thenReturn(1);
    when(passengerService.toResponse(passenger)).thenReturn(dto);
    assertThat(service.dismiss("u-1", "p-1")).isSameAs(dto);
  }

  private static DependentModel dependent(Long id, Long schoolId) {
    DependentModel dependent = new DependentModel();
    dependent.setId(id);
    dependent.setSchoolId(schoolId);
    dependent.setName("Ana");
    return dependent;
  }

  private static UnlinkedPassengerModel passenger(Long id, Long driverId, String name) {
    DriverModel driver = new DriverModel();
    driver.setId(driverId);

    UnlinkedPassengerModel passenger = new UnlinkedPassengerModel();
    passenger.setId(id);
    passenger.setDriver(driver);
    passenger.setName(name);
    return passenger;
  }

  private static FamilyMatchModel match(Long id, Long passengerId, Long dependentId) {
    FamilyMatchModel match = new FamilyMatchModel();
    match.setId(id);
    match.setUnlinkedPassengerId(passengerId);
    match.setDependentId(dependentId);
    match.setStatus(FamilyMatchStatus.PENDING);
    return match;
  }
}
