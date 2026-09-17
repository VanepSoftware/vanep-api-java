package br.com.vanep.trip.repository;

import br.com.vanep.shared.enums.Shift;
import br.com.vanep.trip.model.TripModel;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

  @Query(
      value =
          """
          select trip from TripModel trip
          join fetch trip.driver driver
          join fetch driver.user
          where (:driverId is null or driver.id = :driverId)
          """,
      countQuery =
          "select count(trip) from TripModel trip where (:driverId is null or trip.driver.id = :driverId)")
  Page<TripModel> findPage(@Param("driverId") Long driverId, Pageable pageable);

  @Modifying
  @Query(value = "UPDATE trip SET deleted_at = NULL WHERE token = :token", nativeQuery = true)
  int restoreByToken(@Param("token") String token);

  @Query(
      value = "SELECT count(*) > 0 FROM trip WHERE token = :token AND deleted_at IS NOT NULL",
      nativeQuery = true)
  boolean existsDeletedByToken(@Param("token") String token);

  @Query(
      value =
          "SELECT u.token FROM trip t JOIN driver d ON t.driver_id = d.id JOIN users u ON d.user_id = u.id WHERE t.token = :token",
      nativeQuery = true)
  Optional<String> findDriverUserTokenByTripToken(@Param("token") String token);
}
