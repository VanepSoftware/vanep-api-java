package br.com.vanep.contract.repository;

import br.com.vanep.contract.model.ContractItemModel;
import br.com.vanep.schedule.model.ScheduleSlotModel;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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
      where item.contract.id = :contractId
      """)
  List<ContractItemModel> findByContractId(Long contractId);

  @Query(
      """
      select slot from ContractItemModel item
      join item.schedule schedule
      join schedule.slots slot
      where item.dependent.id = :dependentId
        and item.contract.status = br.com.vanep.contract.enums.ContractStatus.ACTIVE
      """)
  List<ScheduleSlotModel> findActiveSlotsByDependentId(Long dependentId);
}
