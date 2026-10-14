package br.com.vanep.contract.repository;

import br.com.vanep.contract.enums.ContractStatus;
import br.com.vanep.contract.model.ContractItemModel;
import br.com.vanep.routepassenger.dto.RoutePassengerDTO;
import br.com.vanep.schedule.model.ScheduleSlotModel;
import br.com.vanep.shared.enums.OperationShift;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContractItemRepository extends JpaRepository<ContractItemModel, Long> {
  @Query(
      """
      select item from ContractItemModel item
      join fetch item.dependent
      join fetch item.school school
      left join fetch school.city
      left join fetch school.district
      join fetch item.pickupCity
      left join fetch item.pickupDistrict
      join fetch item.schedule schedule
      left join fetch schedule.slots
      where item.contract.id in :contractIds
      order by item.id
      """)
  List<ContractItemModel> findByContractIdIn(Collection<Long> contractIds);

  @Query(
      """
      select slot from ScheduleSlotModel slot
      join fetch slot.schedule schedule
      where schedule.id in (
        select item.schedule.id from ContractItemModel item
        where item.dependent.id = :dependentId and item.contract.status in :statuses)
      """)
  List<ScheduleSlotModel> findSlotsByDependentIdAndContractStatusIn(
      Long dependentId, Collection<ContractStatus> statuses);

  @Query(
      """
      select new br.com.vanep.routepassenger.dto.RoutePassengerDTO(
        br.com.vanep.routepassenger.enums.PassengerSource.CONTRACT_ITEM,
        item.token, dependent.name, school.token, school.name,
        slot.leg, slot.windowStart, slot.windowEnd,
        item.pickupZipCode, item.pickupStreet, item.pickupNumber, item.pickupComplement,
        item.pickupNeighborhood, district.name, city.token, city.name, item.pickupGooglePlaceId)
      from ContractItemModel item
      join item.contract contract
      join item.dependent dependent
      join item.school school
      join item.pickupCity city
      left join item.pickupDistrict district
      join item.schedule schedule
      join schedule.slots slot
      where contract.clientDriver.driver.id = :driverId
        and contract.status = br.com.vanep.contract.enums.ContractStatus.ACTIVE
        and contract.startsOn <= :serviceDate and contract.endsOn >= :serviceDate
        and slot.weekday = :weekday and slot.shift = :shift
      """)
  List<RoutePassengerDTO> findRoutePassengers(
      Long driverId, LocalDate serviceDate, DayOfWeek weekday, OperationShift shift);

  @Modifying
  @Query(
      value =
          """
          UPDATE contract_item SET deleted_at = NULL
          WHERE contract_id = (SELECT id FROM contract WHERE token = :token)
          """,
      nativeQuery = true)
  int restoreByContractToken(@Param("token") String token);

  @Modifying
  @Query(
      value =
          """
          UPDATE schedule SET deleted_at = NULL
          WHERE id IN (
            SELECT item.schedule_id FROM contract_item item
            JOIN contract ON contract.id = item.contract_id
            WHERE contract.token = :token)
          """,
      nativeQuery = true)
  int restoreSchedulesByContractToken(@Param("token") String token);

  // Contract schedules are immutable, so every slot of an item's schedule went away with
  // the contract and comes back with it.
  @Modifying
  @Query(
      value =
          """
          UPDATE schedule_slot SET deleted_at = NULL
          WHERE schedule_id IN (
            SELECT item.schedule_id FROM contract_item item
            JOIN contract ON contract.id = item.contract_id
            WHERE contract.token = :token)
          """,
      nativeQuery = true)
  int restoreSlotsByContractToken(@Param("token") String token);
}
