package br.com.vanep.unlinkedpassenger.service;

import br.com.vanep.dependent.repository.DependentRepository;
import br.com.vanep.familyinvite.model.FamilyInviteModel;
import br.com.vanep.familyinvite.repository.FamilyInviteRepository;
import br.com.vanep.familymatch.dto.FamilyMatchResponseDTO;
import br.com.vanep.familymatch.mapper.FamilyMatchMapper;
import br.com.vanep.familymatch.repository.FamilyMatchRepository;
import br.com.vanep.trip.service.TripService;
import br.com.vanep.unlinkedpassenger.dto.UnlinkedPassengerFamilyDTO;
import br.com.vanep.unlinkedpassenger.dto.UnlinkedPassengerFamilyKeyDTO;
import br.com.vanep.unlinkedpassenger.model.UnlinkedPassengerModel;
import br.com.vanep.vehicle.repository.VehicleRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UnlinkedPassengerFamilyService {

  private final VehicleRepository vehicles;
  private final DependentRepository dependents;
  private final FamilyInviteRepository invites;
  private final FamilyMatchRepository matches;
  private final FamilyMatchMapper matchMapper;

  public UnlinkedPassengerFamilyService(
      VehicleRepository vehicles,
      DependentRepository dependents,
      FamilyInviteRepository invites,
      FamilyMatchRepository matches,
      FamilyMatchMapper matchMapper) {
    this.vehicles = vehicles;
    this.dependents = dependents;
    this.invites = invites;
    this.matches = matches;
    this.matchMapper = matchMapper;
  }

  @Transactional(readOnly = true)
  public UnlinkedPassengerFamilyDTO findFamily(UnlinkedPassengerModel passenger) {
    return findFamilies(List.of(passenger))
        .getOrDefault(passenger.getId(), UnlinkedPassengerFamilyDTO.NONE);
  }

  @Transactional(readOnly = true)
  public Map<Long, UnlinkedPassengerFamilyDTO> findFamilies(
      Collection<UnlinkedPassengerModel> passengers) {
    return findFamiliesByKeys(
        passengers.stream()
            .map(
                passenger ->
                    new UnlinkedPassengerFamilyKeyDTO(
                        passenger.getId(), passenger.getVehicleId(), passenger.getDependentId()))
            .toList());
  }

  @Transactional(readOnly = true)
  public Map<Long, UnlinkedPassengerFamilyDTO> findFamiliesByKeys(
      List<UnlinkedPassengerFamilyKeyDTO> keys) {
    if (keys.isEmpty()) {
      return Map.of();
    }
    List<Long> passengerIds = keys.stream().map(key -> key.passengerId()).toList();
    Map<Long, String> vehicleTokens = findVehicleTokens(keys);
    Map<Long, String> dependentTokens = findDependentTokens(keys);
    Map<Long, Instant> invitedAt = findInvitedAt(passengerIds);
    Map<Long, FamilyMatchResponseDTO> pendingMatches = findPendingMatches(passengerIds);
    return keys.stream()
        .collect(
            Collectors.toMap(
                key -> key.passengerId(),
                key ->
                    new UnlinkedPassengerFamilyDTO(
                        findOrNull(vehicleTokens, key.vehicleId()),
                        findOrNull(dependentTokens, key.dependentId()),
                        invitedAt.get(key.passengerId()),
                        pendingMatches.get(key.passengerId()))));
  }

  Map<Long, String> findVehicleTokens(List<UnlinkedPassengerFamilyKeyDTO> keys) {
    List<Long> ids = distinctIds(keys.stream().map(key -> key.vehicleId()).toList());
    if (ids.isEmpty()) {
      return Map.of();
    }
    return vehicles.findTokensByIdIn(ids).stream()
        .collect(Collectors.toMap(row -> row.getId(), row -> row.getToken()));
  }

  Map<Long, String> findDependentTokens(List<UnlinkedPassengerFamilyKeyDTO> keys) {
    List<Long> ids = distinctIds(keys.stream().map(key -> key.dependentId()).toList());
    if (ids.isEmpty()) {
      return Map.of();
    }
    return dependents.findTokensByIdIn(ids).stream()
        .collect(Collectors.toMap(row -> row.getId(), row -> row.getToken()));
  }

  Map<Long, Instant> findInvitedAt(List<Long> passengerIds) {
    return invites.findByUnlinkedPassengerIdIn(passengerIds).stream()
        .collect(
            Collectors.toMap(
                (FamilyInviteModel invite) -> invite.getUnlinkedPassengerId(),
                invite -> invite.getSentAt()));
  }

  Map<Long, FamilyMatchResponseDTO> findPendingMatches(List<Long> passengerIds) {
    LocalDate today = LocalDate.now(TripService.SERVICE_ZONE);
    return matches.findPendingSummaries(passengerIds).stream()
        .collect(
            Collectors.toMap(
                row -> row.unlinkedPassengerId(), row -> matchMapper.toResponse(row, today)));
  }

  static <V> V findOrNull(Map<Long, V> valuesById, Long id) {
    return id == null ? null : valuesById.get(id);
  }

  static List<Long> distinctIds(List<Long> ids) {
    return ids.stream().filter(Objects::nonNull).distinct().toList();
  }
}
