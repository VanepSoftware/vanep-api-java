package br.com.vanep.unlinkedpassenger.repository;

import br.com.vanep.unlinkedpassenger.model.UnlinkedPassengerModel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UnlinkedPassengerRepository extends JpaRepository<UnlinkedPassengerModel, Long> {
  @Query(
      """
      select passenger from UnlinkedPassengerModel passenger
      join fetch passenger.school school
      left join fetch school.city
      left join fetch school.district
      join fetch passenger.address address
      join fetch address.city
      left join fetch address.district
      join fetch passenger.schedule schedule
      left join fetch schedule.slots
      where passenger.driver.id = :driverId
      order by passenger.name
      """)
  List<UnlinkedPassengerModel> findByDriverId(Long driverId);

  @Query(
      """
      select passenger from UnlinkedPassengerModel passenger
      join fetch passenger.school school
      left join fetch school.city
      left join fetch school.district
      join fetch passenger.address address
      join fetch address.city
      left join fetch address.district
      join fetch passenger.schedule schedule
      left join fetch schedule.slots
      where passenger.token = :token and passenger.driver.id = :driverId
      """)
  Optional<UnlinkedPassengerModel> findByTokenAndDriverId(String token, Long driverId);
}
