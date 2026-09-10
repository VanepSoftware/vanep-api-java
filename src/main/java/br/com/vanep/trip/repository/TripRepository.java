package br.com.vanep.trip.repository;

import br.com.vanep.shared.enums.Shift;
import br.com.vanep.trip.model.TripModel;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TripRepository extends JpaRepository<TripModel, Long> {
  @Query(
      """
      select trip from TripModel trip
      join fetch trip.driver driver
      join fetch driver.user
      where driver.id = :driverId
        and trip.serviceDate = :serviceDate
        and trip.shift = :shift
      """)
  Optional<TripModel> findByDriverAndServiceDateAndShift(
      Long driverId, LocalDate serviceDate, Shift shift);

  @Query(
      """
      select trip from TripModel trip
      join fetch trip.driver driver
      join fetch driver.user
      where driver.id = :driverId
        and trip.serviceDate = :serviceDate
      order by trip.id
      """)
  List<TripModel> findByDriverAndServiceDate(Long driverId, LocalDate serviceDate);

  @Query(
      """
      select trip from TripModel trip
      join fetch trip.driver driver
      join fetch driver.user
      where trip.token = :token
      """)
  Optional<TripModel> findByToken(String token);
}
