package br.com.vanep.schedule.repository;

import br.com.vanep.schedule.dto.ScheduleSlotRowDTO;
import br.com.vanep.schedule.model.ScheduleModel;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ScheduleRepository extends JpaRepository<ScheduleModel, Long> {
  @Query(
      """
      select schedule from ScheduleModel schedule
      left join fetch schedule.slots
      where schedule.id = :id
      """)
  Optional<ScheduleModel> findWithSlotsById(Long id);

  @Query(
      """
      select new br.com.vanep.schedule.dto.ScheduleSlotRowDTO(
        slot.schedule.id, slot.weekday, slot.leg, slot.shift, slot.pickupTime)
      from ScheduleSlotModel slot
      where slot.schedule.id in :scheduleIds
      """)
  List<ScheduleSlotRowDTO> findSlotRowsByScheduleIdIn(Collection<Long> scheduleIds);
}
