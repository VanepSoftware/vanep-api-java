package br.com.vanep.familymatch.service;

import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.dependent.repository.DependentRepository;
import br.com.vanep.familyinvite.model.FamilyInviteModel;
import br.com.vanep.familymatch.enums.FamilyMatchStatus;
import br.com.vanep.familymatch.model.FamilyMatchModel;
import br.com.vanep.familymatch.repository.FamilyMatchRepository;
import br.com.vanep.unlinkedpassenger.dto.UnlinkedPassengerResponseDTO;
import br.com.vanep.unlinkedpassenger.model.UnlinkedPassengerModel;
import br.com.vanep.unlinkedpassenger.repository.UnlinkedPassengerRepository;
import br.com.vanep.unlinkedpassenger.service.UnlinkedPassengerService;
import java.time.Instant;
import java.util.Optional;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FamilyMatchService {

  private final FamilyMatchRepository matches;
  private final UnlinkedPassengerRepository passengers;
  private final UnlinkedPassengerService passengerService;
  private final DependentRepository dependents;
  private final FamilyMatchCandidatePolicy candidatePolicy;
  private final MessageSource messages;

  public FamilyMatchService(
      FamilyMatchRepository matches,
      UnlinkedPassengerRepository passengers,
      UnlinkedPassengerService passengerService,
      DependentRepository dependents,
      FamilyMatchCandidatePolicy candidatePolicy,
      MessageSource messages) {
    this.matches = matches;
    this.passengers = passengers;
    this.passengerService = passengerService;
    this.dependents = dependents;
    this.candidatePolicy = candidatePolicy;
    this.messages = messages;
  }

  @Transactional
  public Optional<FamilyMatchModel> suggestMatch(
      FamilyInviteModel invite, DependentModel dependent) {
    return findCandidate(invite, dependent)
        .map(passenger -> findOrCreatePendingMatch(invite, passenger, dependent));
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
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, notFoundMessage()));
    if (passenger.getDependentId() != null) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, message("family_invite.already_linked"));
    }
    return Optional.of(passenger);
  }

  FamilyMatchModel findOrCreatePendingMatch(
      FamilyInviteModel invite, UnlinkedPassengerModel passenger, DependentModel dependent) {
    Optional<FamilyMatchModel> pending =
        matches.findPendingByUnlinkedPassengerId(passenger.getId());
    if (pending.isPresent()) {
      if (pending.get().getDependentId().equals(dependent.getId())) {
        return pending.get();
      }
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, message("family_match.already_pending"));
    }
    FamilyMatchModel match = new FamilyMatchModel();
    match.setUnlinkedPassengerId(passenger.getId());
    match.setDependentId(dependent.getId());
    match.setFamilyInviteId(invite.getId());
    match.setStatus(FamilyMatchStatus.PENDING);
    return matches.save(match);
  }

  @Transactional
  public UnlinkedPassengerResponseDTO merge(String callerUid, String passengerToken) {
    UnlinkedPassengerModel passenger =
        passengerService.requireOwnPassenger(callerUid, passengerToken);
    FamilyMatchModel match = requirePendingMatch(passenger);
    DependentModel dependent =
        dependents.findById(match.getDependentId()).orElseThrow(() -> notPending());
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

  ResponseStatusException notPending() {
    return new ResponseStatusException(HttpStatus.CONFLICT, message("family_match.not_pending"));
  }

  String notFoundMessage() {
    return message("family_invite.not_found");
  }

  String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }
}
