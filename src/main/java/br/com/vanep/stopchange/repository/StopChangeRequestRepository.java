package br.com.vanep.stopchange.repository;

import br.com.vanep.stopchange.enums.StopChangeStatus;
import br.com.vanep.stopchange.model.StopChangeRequestModel;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StopChangeRequestRepository extends JpaRepository<StopChangeRequestModel, Long> {

  @Query(
      """
      select req from StopChangeRequestModel req
      join fetch req.dependent dep
      join fetch req.trip trip
      join fetch trip.driver driver
      join fetch driver.user
      join fetch req.requestedByUser reqUser
      join fetch req.newDropoffAddress addr
      left join fetch req.respondedByUser respUser
      where req.token = :token
      """)
  Optional<StopChangeRequestModel> findByToken(@Param("token") String token);

  @Query(
      """
      select req from StopChangeRequestModel req
      join fetch req.dependent dep
      join fetch req.trip trip
      join fetch trip.driver driver
      join fetch driver.user
      join fetch req.requestedByUser reqUser
      join fetch req.newDropoffAddress addr
      left join fetch req.respondedByUser respUser
      where trip.id = :tripId
      order by req.id
      """)
  List<StopChangeRequestModel> findByTripId(@Param("tripId") Long tripId);

  @Query(
      """
      select req from StopChangeRequestModel req
      join fetch req.dependent dep
      join fetch req.trip trip
      join fetch trip.driver driver
      join fetch driver.user
      join fetch req.requestedByUser reqUser
      join fetch req.newDropoffAddress addr
      left join fetch req.respondedByUser respUser
      where trip.id = :tripId
        and req.status = :status
      order by req.id
      """)
  List<StopChangeRequestModel> findByTripIdAndStatus(
      @Param("tripId") Long tripId, @Param("status") StopChangeStatus status);

  @Query(
      """
      select req from StopChangeRequestModel req
      join fetch req.dependent dep
      join fetch req.trip trip
      join fetch trip.driver driver
      join fetch driver.user
      join fetch req.requestedByUser reqUser
      join fetch req.newDropoffAddress addr
      left join fetch req.respondedByUser respUser
      where dep.id = :dependentId
        and trip.id = :tripId
        and req.status = :status
      """)
  Optional<StopChangeRequestModel> findByDependentIdAndTripIdAndStatus(
      @Param("dependentId") Long dependentId,
      @Param("tripId") Long tripId,
      @Param("status") StopChangeStatus status);

  @Query(
      """
      select req from StopChangeRequestModel req
      join fetch req.dependent dep
      join fetch req.trip trip
      join fetch trip.driver driver
      join fetch driver.user
      join fetch req.requestedByUser reqUser
      join fetch req.newDropoffAddress addr
      left join fetch req.respondedByUser respUser
      where req.requestedByUser.id = :requestedByUserId
        and req.serviceDate = :serviceDate
      order by req.id
      """)
  List<StopChangeRequestModel> findByRequestedByUserIdAndServiceDate(
      @Param("requestedByUserId") Long requestedByUserId,
      @Param("serviceDate") LocalDate serviceDate);

  @Query(
      """
      select req from StopChangeRequestModel req
      join fetch req.dependent dep
      join fetch req.trip trip
      join fetch trip.driver driver
      join fetch driver.user
      join fetch req.requestedByUser reqUser
      join fetch req.newDropoffAddress addr
      left join fetch req.respondedByUser respUser
      where trip.driver.id = :driverId
        and req.serviceDate = :serviceDate
      order by req.id
      """)
  List<StopChangeRequestModel> findByDriverIdAndServiceDate(
      @Param("driverId") Long driverId, @Param("serviceDate") LocalDate serviceDate);

  @Query(
      value =
          """
          select req from StopChangeRequestModel req
          join fetch req.dependent dep
          join fetch req.trip trip
          join fetch trip.driver driver
          join fetch driver.user
          join fetch req.requestedByUser reqUser
          join fetch req.newDropoffAddress addr
          left join fetch req.respondedByUser respUser
          """,
      countQuery = "select count(req) from StopChangeRequestModel req")
  Page<StopChangeRequestModel> findPage(Pageable pageable);

  @Modifying
  @org.springframework.transaction.annotation.Transactional
  @Query(
      value = "UPDATE stop_change_request SET deleted_at = NULL WHERE token = :token",
      nativeQuery = true)
  int restoreByToken(@Param("token") String token);

  @Query(
      value =
          "SELECT count(*) > 0 FROM stop_change_request WHERE token = :token AND deleted_at IS NOT NULL",
      nativeQuery = true)
  boolean existsDeletedByToken(@Param("token") String token);

  @Query(
      value =
          "SELECT u.token FROM stop_change_request s JOIN users u ON s.requested_by_user_id = u.id WHERE s.token = :token AND s.deleted_at IS NULL",
      nativeQuery = true)
  Optional<String> findRequesterUserTokenByToken(@Param("token") String token);

  @Query(
      value =
          """
          SELECT u.token FROM stop_change_request s
          JOIN trip t ON s.trip_id = t.id
          JOIN driver d ON t.driver_id = d.id
          JOIN users u ON d.user_id = u.id
          WHERE s.token = :token AND s.deleted_at IS NULL
          """,
      nativeQuery = true)
  Optional<String> findDriverUserTokenByToken(@Param("token") String token);
}
