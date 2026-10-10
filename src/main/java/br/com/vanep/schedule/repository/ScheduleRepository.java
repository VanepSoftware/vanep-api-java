package br.com.vanep.schedule.repository;

import br.com.vanep.schedule.model.ScheduleModel;
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
}
