package br.com.vanep.proposal.service;

import br.com.vanep.address.model.AddressModel;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.dependent.model.DependentSchoolDayModel;
import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.proposal.enums.ProposalAcceptanceChange;
import br.com.vanep.proposal.model.ProposalModel;
import br.com.vanep.servicerequest.model.ServiceRequestDayModel;
import br.com.vanep.servicerequest.model.ServiceRequestItemModel;
import br.com.vanep.servicerequest.model.ServiceRequestModel;
import br.com.vanep.shared.enums.SchoolShift;
import br.com.vanep.vehicle.model.VehicleModel;
import java.time.DayOfWeek;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ProposalAcceptancePolicy {

  public Set<ProposalAcceptanceChange> findChanges(
      ServiceRequestModel request,
      ProposalModel proposal,
      List<DependentModel> dependents,
      Map<Long, AddressModel> pickupsById,
      List<DependentSchoolDayModel> grids,
      VehicleModel vehicle,
      DriverModel driver) {
    Set<ProposalAcceptanceChange> changes = EnumSet.noneOf(ProposalAcceptanceChange.class);
    Map<Long, DependentModel> dependentsById = indexBy(dependents, dependent -> dependent.getId());
    Map<Long, Map<DayOfWeek, SchoolShift>> gridsByDependent = gridsByDependent(grids);
    for (ServiceRequestItemModel item : request.getItems()) {
      DependentModel dependent = dependentsById.get(item.getDependentId());
      if (dependent == null) {
        changes.add(ProposalAcceptanceChange.DEPENDENT_REMOVED);
        continue;
      }
      if (!Objects.equals(dependent.getSchoolId(), item.getSchoolId())) {
        changes.add(ProposalAcceptanceChange.SCHOOL_CHANGED);
      }
      if (hasAnotherPickup(item, pickupsById.get(dependent.getAddressId()))) {
        changes.add(ProposalAcceptanceChange.PICKUP_CHANGED);
      }
      if (hasAnotherSchoolDay(item, gridsByDependent.getOrDefault(dependent.getId(), Map.of()))) {
        changes.add(ProposalAcceptanceChange.SCHOOL_DAY_CHANGED);
      }
    }
    if (isAnotherVehicle(proposal, vehicle, driver)) {
      changes.add(ProposalAcceptanceChange.VEHICLE_CHANGED);
    }
    if (!driver.isActive() || driver.getApprovalStatus() != DriverApprovalStatus.APPROVED) {
      changes.add(ProposalAcceptanceChange.DRIVER_UNAVAILABLE);
    }
    return changes;
  }

  boolean hasAnotherPickup(ServiceRequestItemModel item, AddressModel pickup) {
    return pickup == null
        || !Objects.equals(pickup.getCity().getId(), item.getPickupCityId())
        || !normalized(pickup.getNeighborhood())
            .equals(normalized(item.getSnapshot().pickup().neighborhood()));
  }

  boolean hasAnotherSchoolDay(ServiceRequestItemModel item, Map<DayOfWeek, SchoolShift> grid) {
    for (ServiceRequestDayModel day : item.getDays()) {
      if (grid.get(day.getWeekday()) != day.getSchoolShift()) {
        return true;
      }
    }
    return false;
  }

  boolean isAnotherVehicle(ProposalModel proposal, VehicleModel vehicle, DriverModel driver) {
    ProposalModel.Snapshot.Vehicle offered = proposal.getSnapshot().vehicle();
    return vehicle == null
        || !vehicle.isActive()
        || !Objects.equals(vehicle.getDriver().getId(), driver.getId())
        || !Objects.equals(vehicle.getPlate(), offered.plate())
        || !Objects.equals(vehicle.getCapacity(), offered.capacity());
  }

  String normalized(String neighborhood) {
    return neighborhood == null ? "" : neighborhood.strip().toLowerCase(Locale.ROOT);
  }

  Map<Long, Map<DayOfWeek, SchoolShift>> gridsByDependent(List<DependentSchoolDayModel> grids) {
    return grids.stream()
        .collect(
            Collectors.groupingBy(
                day -> day.getDependentId(),
                Collectors.toMap(day -> day.getWeekday(), day -> day.getSchoolShift())));
  }

  <K, V> Map<K, V> indexBy(Collection<V> values, Function<V, K> key) {
    return values.stream().collect(Collectors.toMap(key, value -> value));
  }
}
