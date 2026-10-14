package br.com.vanep.unlinkedpassenger.repository;

import br.com.vanep.routepassenger.dto.RoutePassengerDTO;
import br.com.vanep.shared.enums.OperationShift;
import br.com.vanep.unlinkedpassenger.model.UnlinkedPassengerModel;
import java.time.DayOfWeek;
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

  @Query(
      """
      select new br.com.vanep.routepassenger.dto.RoutePassengerDTO(
        br.com.vanep.routepassenger.enums.PassengerSource.UNLINKED_PASSENGER,
        passenger.token, passenger.name, school.token, school.name,
        slot.leg, slot.windowStart, slot.windowEnd,
        address.zipCode, address.street, address.number, address.complement,
        address.neighborhood, district.name, city.token, city.name, address.googlePlaceId)
      from UnlinkedPassengerModel passenger
      join passenger.school school
      join passenger.address address
      join address.city city
      left join address.district district
      join passenger.schedule schedule
      join schedule.slots slot
      where passenger.driver.id = :driverId
        and slot.weekday = :weekday and slot.shift = :shift
      """)
  List<RoutePassengerDTO> findRoutePassengers(
      Long driverId, DayOfWeek weekday, OperationShift shift);
}
