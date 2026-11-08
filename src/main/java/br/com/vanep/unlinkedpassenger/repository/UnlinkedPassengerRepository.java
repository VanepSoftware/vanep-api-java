package br.com.vanep.unlinkedpassenger.repository;

import br.com.vanep.driveroccupancy.dto.ShiftOccupancyDTO;
import br.com.vanep.driverstudent.dto.UnlinkedStudentRowDTO;
import br.com.vanep.routepassenger.dto.RoutePassengerDTO;
import br.com.vanep.shared.enums.OperationShift;
import br.com.vanep.unlinkedpassenger.model.UnlinkedPassengerModel;
import java.time.DayOfWeek;
import java.util.Collection;
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
      join fetch passenger.address address
      join fetch address.city
      join fetch passenger.schedule schedule
      left join fetch schedule.slots
      where passenger.driver.id = :driverId
      order by passenger.name
      """)
  List<UnlinkedPassengerModel> findByDriverId(Long driverId);

  @Query(
      """
      select new br.com.vanep.driverstudent.dto.UnlinkedStudentRowDTO(
        passenger.id, passenger.token, passenger.name, school.token, school.name,
        passenger.schoolShift, passenger.vehicleId, passenger.dependentId, schedule.id)
      from UnlinkedPassengerModel passenger
      join passenger.school school
      join passenger.schedule schedule
      where passenger.driver.id = :driverId
        and (:vehicleId is null or passenger.vehicleId is null or passenger.vehicleId = :vehicleId)
      """)
  List<UnlinkedStudentRowDTO> findStudentsOfDriver(Long driverId, Long vehicleId);

  List<UnlinkedPassengerModel> findByDriverIdAndDependentIdIn(
      Long driverId, Collection<Long> dependentIds);

  @Query(
      """
      select passenger from UnlinkedPassengerModel passenger
      where passenger.driver.id = :driverId
        and passenger.school.id = :schoolId
        and passenger.dependentId is null
      """)
  List<UnlinkedPassengerModel> findUnmergedAtSchool(Long driverId, Long schoolId);

  @Query(
      """
      select passenger from UnlinkedPassengerModel passenger
      join fetch passenger.school school
      left join fetch school.city
      join fetch passenger.address address
      join fetch address.city
      join fetch passenger.schedule schedule
      left join fetch schedule.slots
      where passenger.token = :token and passenger.driver.id = :driverId
      """)
  Optional<UnlinkedPassengerModel> findByTokenAndDriverId(String token, Long driverId);

  @Query(
      """
      select new br.com.vanep.routepassenger.dto.RoutePassengerDTO(
        br.com.vanep.routepassenger.enums.PassengerSource.UNLINKED_PASSENGER,
        passenger.token, passenger.name, dependent.token, school.token, school.name,
        slot.leg, slot.pickupTime,
        address.zipCode, address.street, address.number, address.complement,
        address.neighborhood, city.token, city.name, address.googlePlaceId)
      from UnlinkedPassengerModel passenger
      join passenger.school school
      join passenger.address address
      join address.city city
      join passenger.schedule schedule
      join schedule.slots slot
      left join DependentModel dependent on dependent.id = passenger.dependentId
      where passenger.driver.id = :driverId
        and slot.weekday = :weekday and slot.shift = :shift
      """)
  List<RoutePassengerDTO> findRoutePassengers(
      Long driverId, DayOfWeek weekday, OperationShift shift);

  @Query(
      """
      select new br.com.vanep.driveroccupancy.dto.ShiftOccupancyDTO(
        slot.shift, count(distinct passenger.id))
      from UnlinkedPassengerModel passenger
      join passenger.schedule schedule
      join schedule.slots slot
      where passenger.driver.id = :driverId
        and (:vehicleId is null or passenger.vehicleId is null or passenger.vehicleId = :vehicleId)
      group by slot.shift
      """)
  List<ShiftOccupancyDTO> countPassengersByShift(Long driverId, Long vehicleId);

  @Query(
      """
      select count(passenger) from UnlinkedPassengerModel passenger
      where passenger.driver.id = :driverId
        and (:vehicleId is null or passenger.vehicleId is null or passenger.vehicleId = :vehicleId)
      """)
  long countStudents(Long driverId, Long vehicleId);

  @Query(
      """
      select count(passenger) from UnlinkedPassengerModel passenger
      where passenger.driver.id = :driverId and passenger.dependentId is not null
        and (:vehicleId is null or passenger.vehicleId is null or passenger.vehicleId = :vehicleId)
      """)
  long countMergedStudents(Long driverId, Long vehicleId);
}
