package br.com.vanep.familyinvite.service;

import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.clientdriver.enums.LinkOrigin;
import br.com.vanep.clientdriver.enums.RelationshipStatus;
import br.com.vanep.clientdriver.model.ClientDriverModel;
import br.com.vanep.clientdriver.service.ClientDriverService;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.dependent.repository.DependentRepository;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.familyinvite.dto.FamilyInviteAcceptRequestDTO;
import br.com.vanep.familyinvite.dto.FamilyInviteAcceptResponseDTO;
import br.com.vanep.familyinvite.dto.FamilyInvitePreviewResponseDTO;
import br.com.vanep.familyinvite.dto.FamilyInviteResponseDTO;
import br.com.vanep.familyinvite.model.FamilyInviteModel;
import br.com.vanep.familyinvite.repository.FamilyInviteRepository;
import br.com.vanep.familymatch.model.FamilyMatchModel;
import br.com.vanep.familymatch.service.FamilyMatchService;
import br.com.vanep.unlinkedpassenger.model.UnlinkedPassengerModel;
import br.com.vanep.unlinkedpassenger.repository.UnlinkedPassengerRepository;
import br.com.vanep.unlinkedpassenger.service.UnlinkedPassengerService;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.service.UserService;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FamilyInviteService {

  private final FamilyInviteRepository invites;
  private final UnlinkedPassengerRepository passengers;
  private final UnlinkedPassengerService passengerService;
  private final DriverRepository drivers;
  private final ClientRepository clients;
  private final DependentRepository dependents;
  private final UserService users;
  private final ClientDriverService links;
  private final FamilyMatchService matchService;
  private final FamilyInviteCodeGenerator codeGenerator;
  private final MessageSource messages;
  private final String inviteBaseUrl;

  public FamilyInviteService(
      FamilyInviteRepository invites,
      UnlinkedPassengerRepository passengers,
      UnlinkedPassengerService passengerService,
      DriverRepository drivers,
      ClientRepository clients,
      DependentRepository dependents,
      UserService users,
      ClientDriverService links,
      FamilyMatchService matchService,
      FamilyInviteCodeGenerator codeGenerator,
      MessageSource messages,
      @Value("${vanep.app.invite-base-url}") String inviteBaseUrl) {
    this.invites = invites;
    this.passengers = passengers;
    this.passengerService = passengerService;
    this.drivers = drivers;
    this.clients = clients;
    this.dependents = dependents;
    this.users = users;
    this.links = links;
    this.matchService = matchService;
    this.codeGenerator = codeGenerator;
    this.messages = messages;
    this.inviteBaseUrl = inviteBaseUrl;
  }

  @Transactional
  public FamilyInviteResponseDTO inviteFamily(String callerUid, String passengerToken) {
    UnlinkedPassengerModel passenger =
        passengerService.requireOwnPassenger(callerUid, passengerToken);
    if (passenger.getDependentId() != null) {
      throw conflict("family_invite.already_linked");
    }
    FamilyInviteModel invite =
        invites
            .findByUnlinkedPassengerId(passenger.getId())
            .orElseGet(() -> newInvite(passenger.getDriver().getId(), passenger.getId()));
    invite.setSentAt(Instant.now());
    return toResponse(writeOrConflict(() -> invites.saveAndFlush(invite)));
  }

  @Transactional
  public FamilyInviteResponseDTO findGroupInvite(String callerUid) {
    DriverModel driver = passengerService.requireDriver(callerUid);
    FamilyInviteModel invite =
        invites
            .findGroupInviteByDriverId(driver.getId())
            .orElseGet(
                () -> {
                  FamilyInviteModel created = newInvite(driver.getId(), null);
                  created.setSentAt(Instant.now());
                  return writeOrConflict(() -> invites.saveAndFlush(created));
                });
    return toResponse(invite);
  }

  @Transactional(readOnly = true)
  public FamilyInvitePreviewResponseDTO preview(String code) {
    FamilyInviteModel invite = requireInvite(code);
    DriverModel driver = requireDriverOf(invite);
    Optional<UnlinkedPassengerModel> passenger =
        Optional.ofNullable(invite.getUnlinkedPassengerId()).flatMap(id -> passengers.findById(id));
    return new FamilyInvitePreviewResponseDTO(
        firstNameOf(driver.getUser().getName()),
        passenger.map(found -> found.getSchool().getName()).orElse(null),
        passenger.map(found -> firstNameOf(found.getName())).orElse(null));
  }

  @Transactional
  public FamilyInviteAcceptResponseDTO accept(
      String callerUid, String code, FamilyInviteAcceptRequestDTO request) {
    ClientModel client = requireCallerClient(callerUid);
    FamilyInviteModel invite = requireInvite(code);
    DependentModel dependent = requireOwnDependent(client, request.dependentToken());
    DriverModel driver = requireDriverOf(invite);
    ClientDriverModel link =
        writeOrConflict(() -> links.findOrCreate(client, driver, LinkOrigin.DRIVER_INVITE));
    if (link.getStatus() == RelationshipStatus.BLOCKED) {
      throw conflict("family_invite.link_blocked");
    }
    Optional<FamilyMatchModel> match =
        writeOrConflict(() -> matchService.suggestMatch(invite, dependent));
    return new FamilyInviteAcceptResponseDTO(
        link.getToken(), match.map(found -> found.getToken()).orElse(null));
  }

  // A concurrent twin of the same request wins one of the partial unique indexes; the loser answers
  // 409 instead of 500, and a retry reads what the winner wrote.
  <T> T writeOrConflict(Supplier<T> write) {
    try {
      return write.get();
    } catch (DataIntegrityViolationException concurrentWrite) {
      throw conflict("family_invite.concurrent_request");
    }
  }

  FamilyInviteModel newInvite(Long driverId, Long passengerId) {
    FamilyInviteModel invite = new FamilyInviteModel();
    invite.setDriverId(driverId);
    invite.setUnlinkedPassengerId(passengerId);
    invite.setCode(generateUniqueCode());
    return invite;
  }

  String generateUniqueCode() {
    String code = codeGenerator.generateCode();
    while (invites.existsByCode(code)) {
      code = codeGenerator.generateCode();
    }
    return code;
  }

  FamilyInviteResponseDTO toResponse(FamilyInviteModel invite) {
    return new FamilyInviteResponseDTO(
        invite.getCode(), inviteUrlOf(invite.getCode()), invite.getSentAt());
  }

  String inviteUrlOf(String code) {
    return inviteBaseUrl.endsWith("/") ? inviteBaseUrl + code : inviteBaseUrl + "/" + code;
  }

  static String firstNameOf(String name) {
    if (name == null || name.isBlank()) {
      return null;
    }
    return name.strip().split("\\s+")[0];
  }

  FamilyInviteModel requireInvite(String code) {
    return invites
        .findByCode(code)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, message("family_invite.not_found")));
  }

  DriverModel requireDriverOf(FamilyInviteModel invite) {
    return drivers
        .findById(invite.getDriverId())
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, message("family_invite.not_found")));
  }

  ClientModel requireCallerClient(String callerUid) {
    UserModel caller = users.requireByTokenAndType(callerUid, UserType.CLIENT);
    return clients
        .findByUserId(caller.getId())
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, message("client.profile.not_found")));
  }

  DependentModel requireOwnDependent(ClientModel client, String dependentToken) {
    return dependents
        .findByToken(dependentToken)
        .filter(dependent -> dependent.getClientId().equals(client.getId()))
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, message("family_invite.dependent.not_found")));
  }

  ResponseStatusException conflict(String key) {
    return new ResponseStatusException(HttpStatus.CONFLICT, message(key));
  }

  String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }
}
