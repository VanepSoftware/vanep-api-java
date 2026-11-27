package br.com.vanep.familymatch.service;

import br.com.vanep.contract.repository.ContractItemRepository;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.dependent.repository.DependentRepository;
import br.com.vanep.familyinvite.model.FamilyInviteModel;
import br.com.vanep.familymatch.enums.FamilyMatchStatus;
import br.com.vanep.familymatch.model.FamilyMatchModel;
import br.com.vanep.familymatch.repository.FamilyMatchRepository;
import br.com.vanep.shared.error.ApiProblemException;
import br.com.vanep.unlinkedpassenger.dto.UnlinkedPassengerResponseDTO;
import br.com.vanep.unlinkedpassenger.model.UnlinkedPassengerModel;
import br.com.vanep.unlinkedpassenger.repository.UnlinkedPassengerRepository;
import br.com.vanep.unlinkedpassenger.service.UnlinkedPassengerService;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FamilyMatchService {

  private final FamilyMatchRepository matches;
  private final UnlinkedPassengerRepository passengers;
  private final UnlinkedPassengerService passengerService;
  private final DependentRepository dependents;
  private final ContractItemRepository contractItems;
  private final FamilyMatchCandidatePolicy candidatePolicy;

  public FamilyMatchService(
      FamilyMatchRepository matches,
      UnlinkedPassengerRepository passengers,
      UnlinkedPassengerService passengerService,
      DependentRepository dependents,
      ContractItemRepository contractItems,
      FamilyMatchCandidatePolicy candidatePolicy) {
    this.matches = matches;
    this.passengers = passengers;
    this.passengerService = passengerService;
    this.dependents = dependents;
    this.contractItems = contractItems;
    this.candidatePolicy = candidatePolicy;
  }

  @Transactional
  public Optional<FamilyMatchModel> suggestMatch(
      FamilyInviteModel invite, DependentModel dependent) {
    return findCandidate(invite, dependent)
        .flatMap(passenger -> findOrCreatePendingMatch(invite, passenger, dependent));
  }

  Optional<UnlinkedPassengerModel> findCandidate(
      FamilyInviteModel invite, DependentModel dependent) {
    if (invite.isGroupInvite()) {
      if (dependent.getSchoolId() == null) {
        return Optional.empty();
      }
      return candidatePolicy.pickCandidate(
          dependent.getName(),
          passengers.findUnmergedAtSchool(invite.getDriverId(), dependent.getSchoolId()));
    }
    UnlinkedPassengerModel passenger =
        passengers
            .findById(invite.getUnlinkedPassengerId())
            .orElseThrow(() -> ApiProblemException.notFound("family_invite.not_found"));
    if (passenger.getDependentId() != null) {
      throw ApiProblemException.conflict("family_invite.already_linked");
    }
    return Optional.of(passenger);
  }

  Optional<FamilyMatchModel> findOrCreatePendingMatch(
      FamilyInviteModel invite, UnlinkedPassengerModel passenger, DependentModel dependent) {
    matches.dismissPendingIfFamilyGone(passenger.getId(), Instant.now());
    Optional<FamilyMatchModel> pending =
        matches.findPendingByUnlinkedPassengerId(passenger.getId());
    if (pending.isPresent()) {
      if (pending.get().getDependentId().equals(dependent.getId())) {
        return pending;
      }
      if (invite.isGroupInvite()) {
        return Optional.empty();
      }
      throw ApiProblemException.conflict("family_match.already_pending");
    }
    FamilyMatchModel match = new FamilyMatchModel();
    match.setUnlinkedPassengerId(passenger.getId());
    match.setDependentId(dependent.getId());
    match.setFamilyInviteId(invite.getId());
    match.setStatus(FamilyMatchStatus.PENDING);
    return Optional.of(matches.save(match));
  }

  @Transactional
  public UnlinkedPassengerResponseDTO merge(String callerUid, String passengerToken) {
    UnlinkedPassengerModel passenger =
        passengerService.requireOwnPassenger(callerUid, passengerToken);
    matches.dismissPendingIfFamilyGone(passenger.getId(), Instant.now());
    FamilyMatchModel match = requirePendingMatch(passenger);
    DependentModel dependent =
        dependents.findById(match.getDependentId()).orElseThrow(() -> notPending());
    if (passengers.existsByDriver_IdAndDependentIdAndIdNot(
            passenger.getDriver().getId(), dependent.getId(), passenger.getId())
        || contractItems.countActiveForDriverAndDependent(
                passenger.getDriver().getId(), dependent.getId())
            > 0) {
      throw ApiProblemException.conflict("family_match.dependent_already_linked");
    }
    respond(match, FamilyMatchStatus.CONFIRMED);
    passenger.setDependentId(dependent.getId());
    passenger.setName(dependent.getName());
    return passengerService.toResponse(passengers.saveAndFlush(passenger));
  }

  @Transactional
  public UnlinkedPassengerResponseDTO dismiss(String callerUid, String passengerToken) {
    UnlinkedPassengerModel passenger =
        passengerService.requireOwnPassenger(callerUid, passengerToken);
    respond(requirePendingMatch(passenger), FamilyMatchStatus.DISMISSED);
    return passengerService.toResponse(passenger);
  }

  FamilyMatchModel requirePendingMatch(UnlinkedPassengerModel passenger) {
    return matches
        .findPendingByUnlinkedPassengerId(passenger.getId())
        .orElseThrow(() -> notPending());
  }

  void respond(FamilyMatchModel match, FamilyMatchStatus status) {
    if (matches.respondIfPending(match.getId(), status.name(), Instant.now()) == 0) {
      throw notPending();
    }
  }

  ApiProblemException notPending() {
    return ApiProblemException.conflict("family_match.not_pending");
  }
}
