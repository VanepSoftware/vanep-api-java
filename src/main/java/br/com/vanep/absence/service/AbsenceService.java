package br.com.vanep.absence.service;

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
import br.com.vanep.trip.enums.TripStatus;
import br.com.vanep.trip.model.TripModel;
import br.com.vanep.trip.repository.TripRepository;
import br.com.vanep.trip.service.TripService;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.service.UserService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AbsenceService {
  private final AbsenceRepository absences;
  private final ClientDriverRepository links;
  private final DependentRepository dependents;
  private final ClientRepository clients;
  private final TripRepository trips;
  private final AssistantRepository assistants;
  private final UserService users;
  private final AbsenceLegShiftPolicy legs;
  private final AbsenceScopePolicy scopes;
  private final AbsenceUndoPolicy undos;
  private final AbsenceDayEffectPort effects;
  private final AbsenceCounterpartNotifier notifier;
  private final MessageSource messages;

  public AbsenceService(
      AbsenceRepository absences,
      ClientDriverRepository links,
      DependentRepository dependents,
      ClientRepository clients,
      TripRepository trips,
      AssistantRepository assistants,
      UserService users,
      AbsenceLegShiftPolicy legs,
      AbsenceScopePolicy scopes,
      AbsenceUndoPolicy undos,
      AbsenceDayEffectPort effects,
      AbsenceCounterpartNotifier notifier,
      MessageSource messages) {
    this.absences = absences;
    this.links = links;
    this.dependents = dependents;
    this.clients = clients;
    this.trips = trips;
    this.assistants = assistants;
    this.users = users;
    this.legs = legs;
    this.scopes = scopes;
    this.undos = undos;
    this.effects = effects;
    this.notifier = notifier;
    this.messages = messages;
  }

  @Transactional
  public AbsenceMutationResult reportForClient(
      String callerUid, String linkToken, String dependentToken, AbsenceScope scope) {
    ClientDriverModel link = requireOwnedActiveLink(callerUid, linkToken);
    DependentModel dependent = requireOwnedDependent(link.getClient(), dependentToken);
    requireScopeAllowed(scope, dependent);
    return persistLegs(link, dependent, scopes.expand(scope), AbsenceSource.CLIENT, null, null);
  }

  @Transactional
  public void undoForClient(
      String callerUid, String linkToken, String dependentToken, AbsenceScope scope) {
    ClientDriverModel link = requireOwnedActiveLink(callerUid, linkToken);
    DependentModel dependent = requireOwnedDependent(link.getClient(), dependentToken);
    requireScopeAllowed(scope, dependent);
    LocalDate today = today();
    List<AbsenceModel> targets = new ArrayList<>();
    List<TripStatus> statuses = new ArrayList<>();
    for (AbsenceLeg leg : scopes.expand(scope)) {
      absences
          .findByDependentAndDateAndLeg(dependent.getId(), today, leg)
          .filter(row -> row.getSource() == AbsenceSource.CLIENT)
          .ifPresent(
              row -> {
                targets.add(row);
                statuses.add(tripStatusFor(row, link.getDriver(), leg));
              });
    }
    if (!undos.allowsAll(statuses)) {
      throw conflict("absence.undo.trip_started");
    }
    targets.forEach(absences::delete);
  }

  @Transactional(readOnly = true)
  public List<AbsenceModel> listTodayForClient(
      String callerUid, String linkToken, String dependentToken) {
    ClientDriverModel link = requireOwnedActiveLink(callerUid, linkToken);
    DependentModel dependent = requireOwnedDependent(link.getClient(), dependentToken);
    return absences.findByDependentAndDate(dependent.getId(), today());
  }

  @Transactional
  public AbsenceMutationResult reportNoShow(
      String callerUid, String tripToken, String dependentToken, String reason) {
    if (reason == null || reason.isBlank()) {
      throw badRequest("absence.reason.required");
    }
    TripModel trip = requireOpenTripToday(tripToken);
    AbsenceSource source = requireTripOperator(callerUid, trip);
    DependentModel dependent = requireDependentOnVan(trip.getDriver(), dependentToken);
    List<AbsenceLeg> targetLegs = noShowLegs(trip, dependent);
    ClientDriverModel link =
        links
            .findByPair(dependent.getClientId(), trip.getDriver().getId())
            .orElseThrow(() -> notFound("absence.link.not_found"));
    return persistLegs(link, dependent, targetLegs, source, reason.trim(), trip);
  }

  LocalDate today() {
    return LocalDate.now(TripService.SERVICE_ZONE);
  }

  private AbsenceMutationResult persistLegs(
      ClientDriverModel link,
      DependentModel dependent,
      List<AbsenceLeg> targetLegs,
      AbsenceSource source,
      String reason,
      TripModel knownTrip) {
    LocalDate today = today();
    List<AbsenceModel> stored = new ArrayList<>();
    boolean createdAny = false;
    for (AbsenceLeg leg : targetLegs) {
      TripModel trip =
          knownTrip != null && legs.covers(knownTrip.getShift(), leg)
              ? knownTrip
              : matchingTrip(link.getDriver(), leg).orElse(null);
      InsertedAbsence inserted = insertOrReread(link, dependent, today, leg, source, reason, trip);
      createdAny = createdAny || inserted.created();
      effects.onAbsenceRecorded(inserted.absence());
      notifier.notifyCounterpart(inserted.absence());
      stored.add(inserted.absence());
    }
    return new AbsenceMutationResult(List.copyOf(stored), createdAny);
  }

  private InsertedAbsence insertOrReread(
      ClientDriverModel link,
      DependentModel dependent,
      LocalDate date,
      AbsenceLeg leg,
      AbsenceSource source,
      String reason,
      TripModel trip) {
    Optional<AbsenceModel> existing =
        absences.findByDependentAndDateAndLeg(dependent.getId(), date, leg);
    if (existing.isPresent()) {
      return new InsertedAbsence(existing.get(), false);
    }
    AbsenceModel row = new AbsenceModel();
    row.setClientDriver(link);
    row.setDependent(dependent);
    row.setAbsenceDate(date);
    row.setLeg(leg);
    row.setSource(source);
    row.setReason(reason);
    row.setTrip(trip);
    try {
      return new InsertedAbsence(absences.saveAndFlush(row), true);
    } catch (DataIntegrityViolationException ignored) {
      AbsenceModel winner =
          absences
              .findByDependentAndDateAndLeg(dependent.getId(), date, leg)
              .orElseThrow(() -> conflict("absence.conflict.reread_missed"));
      return new InsertedAbsence(winner, false);
    }
  }

  private record InsertedAbsence(AbsenceModel absence, boolean created) {}

  private ClientDriverModel requireOwnedActiveLink(String callerUid, String linkToken) {
    UserModel user = users.requireByTokenAndType(callerUid, UserType.CLIENT);
    ClientModel client =
        clients.findByUserId(user.getId()).orElseThrow(() -> notFound("client.profile.not_found"));
    ClientDriverModel link =
        links.findByToken(linkToken).orElseThrow(() -> notFound("absence.link.not_found"));
    if (!link.getClient().getId().equals(client.getId())) {
      throw forbidden("absence.forbidden");
    }
    if (link.getStatus() != RelationshipStatus.ACTIVE) {
      throw conflict("absence.link.not_active");
    }
    return link;
  }

  private DependentModel requireOwnedDependent(ClientModel client, String dependentToken) {
    DependentModel dependent =
        dependents.findByToken(dependentToken).orElseThrow(() -> notFound("dependent.not_found"));
    if (!dependent.getClientId().equals(client.getId())) {
      throw forbidden("absence.forbidden");
    }
    return dependent;
  }

  private void requireScopeAllowed(AbsenceScope scope, DependentModel dependent) {
    if (!scopes.allows(scope, dependent.getShift())) {
      throw badRequest("absence.scope.not_in_shift");
    }
  }

  private Optional<TripModel> matchingTrip(DriverModel driver, AbsenceLeg leg) {
    return trips.findByDriverAndServiceDate(driver.getId(), today()).stream()
        .filter(trip -> legs.covers(trip.getShift(), leg))
        .findFirst();
  }

  private TripStatus tripStatusFor(AbsenceModel row, DriverModel driver, AbsenceLeg leg) {
    if (row.getTrip() != null) {
      return row.getTrip().getStatus();
    }
    return matchingTrip(driver, leg).map(TripModel::getStatus).orElse(null);
  }

  private TripModel requireOpenTripToday(String tripToken) {
    TripModel trip = trips.findByToken(tripToken).orElseThrow(() -> notFound("trip.not_found"));
    if (!today().equals(trip.getServiceDate())) {
      throw conflict("absence.trip.not_today");
    }
    if (trip.getStatus() == TripStatus.COMPLETED || trip.getStatus() == TripStatus.CANCELLED) {
      throw conflict("absence.trip.not_open");
    }
    return trip;
  }

  private AbsenceSource requireTripOperator(String callerUid, TripModel trip) {
    UserModel caller = users.requireByToken(callerUid);
    String driverUid = trip.getDriver().getUser().getToken();
    if (caller.getType() == UserType.DRIVER && callerUid.equals(driverUid)) {
      return AbsenceSource.DRIVER;
    }
    if (caller.getType() == UserType.ASSISTANT) {
      AssistantModel assistant =
          assistants
              .findByUserId(caller.getId())
              .orElseThrow(() -> notFound("assistant.profile.not_found"));
      if (assistant.getStatus() == AssistantStatus.ACTIVE
          && assistant.getDriver() != null
          && assistant.getDriver().getId().equals(trip.getDriver().getId())) {
        return AbsenceSource.ASSISTANT;
      }
    }
    throw forbidden("absence.forbidden");
  }

  private DependentModel requireDependentOnVan(DriverModel driver, String dependentToken) {
    DependentModel dependent =
        dependents.findByToken(dependentToken).orElseThrow(() -> notFound("dependent.not_found"));
    ClientDriverModel link =
        links
            .findByPair(dependent.getClientId(), driver.getId())
            .orElseThrow(() -> notFound("absence.link.not_found"));
    if (link.getStatus() != RelationshipStatus.ACTIVE) {
      throw conflict("absence.link.not_active");
    }
    return dependent;
  }

  private List<AbsenceLeg> noShowLegs(TripModel trip, DependentModel dependent) {
    return legs.uniqueLegOf(trip.getShift())
        .filter(leg -> scopes.allows(scopeOf(leg), dependent.getShift()))
        .map(List::of)
        .orElseGet(
            () -> {
              if (!legs.covers(trip.getShift(), AbsenceLeg.OUTBOUND)
                  && !legs.covers(trip.getShift(), AbsenceLeg.RETURN)) {
                throw badRequest("absence.scope.not_in_shift");
              }
              if (!scopes.allows(AbsenceScope.BOTH, dependent.getShift())
                  && !scopes.allows(AbsenceScope.OUTBOUND, dependent.getShift())
                  && !scopes.allows(AbsenceScope.RETURN, dependent.getShift())) {
                throw badRequest("absence.scope.not_in_shift");
              }
              List<AbsenceLeg> covered = new ArrayList<>();
              for (AbsenceLeg leg : List.of(AbsenceLeg.OUTBOUND, AbsenceLeg.RETURN)) {
                if (legs.covers(trip.getShift(), leg)
                    && scopes.allows(scopeOf(leg), dependent.getShift())) {
                  covered.add(leg);
                }
              }
              if (covered.isEmpty()) {
                throw badRequest("absence.scope.not_in_shift");
              }
              return covered;
            });
  }

  private AbsenceScope scopeOf(AbsenceLeg leg) {
    return leg == AbsenceLeg.OUTBOUND ? AbsenceScope.OUTBOUND : AbsenceScope.RETURN;
  }

  private ResponseStatusException conflict(String key) {
    return new ResponseStatusException(HttpStatus.CONFLICT, message(key));
  }

  private ResponseStatusException notFound(String key) {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, message(key));
  }

  private ResponseStatusException badRequest(String key) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message(key));
  }

  private ResponseStatusException forbidden(String key) {
    return new ResponseStatusException(HttpStatus.FORBIDDEN, message(key));
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }
}
