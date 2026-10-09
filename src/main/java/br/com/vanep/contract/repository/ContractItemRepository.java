package br.com.vanep.contract.repository;

import br.com.vanep.contract.enums.ContractStatus;
import br.com.vanep.contract.model.ContractItemModel;
import br.com.vanep.schedule.model.ScheduleSlotModel;
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
