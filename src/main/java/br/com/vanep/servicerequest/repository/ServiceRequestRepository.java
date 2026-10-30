package br.com.vanep.servicerequest.repository;

import br.com.vanep.servicerequest.enums.ServiceRequestStatus;
import br.com.vanep.servicerequest.model.ServiceRequestModel;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequestModel, Long> {
  @Query(
      """
      select distinct request from ServiceRequestModel request
      join fetch request.clientDriver link
      join fetch link.client client
      join fetch client.user
      join fetch link.driver driver
      join fetch driver.user
      join fetch request.items item
      join fetch item.days
      where request.token = :token and client.id = :clientId
      """)
  Optional<ServiceRequestModel> findByTokenAndClientId(String token, Long clientId);

  @Query(
      """
      select distinct request from ServiceRequestModel request
      join fetch request.clientDriver link
      join fetch link.client client
      join fetch client.user
      join fetch link.driver driver
      join fetch driver.user
      join fetch request.items item
      join fetch item.days
      where request.token = :token and driver.id = :driverId
      """)
  Optional<ServiceRequestModel> findByTokenAndDriverId(String token, Long driverId);

  @Query(
      value =
          """
          select request from ServiceRequestModel request
          join fetch request.clientDriver link
          join fetch link.client client
          join fetch client.user
          join fetch link.driver driver
          join fetch driver.user
          where client.id = :clientId and request.status in :statuses
          order by request.createdAt desc, request.id desc
          """,
      countQuery =
          """
          select count(request) from ServiceRequestModel request
          where request.clientDriver.client.id = :clientId and request.status in :statuses
          """)
  Page<ServiceRequestModel> findPageByClientIdAndStatusIn(
      Long clientId, Collection<ServiceRequestStatus> statuses, Pageable pageable);

  @Query(
      value =
          """
          select request from ServiceRequestModel request
          join fetch request.clientDriver link
          join fetch link.client client
          join fetch client.user
          join fetch link.driver driver
          join fetch driver.user
          where driver.id = :driverId and request.status in :statuses
          order by request.createdAt desc, request.id desc
          """,
      countQuery =
          """
          select count(request) from ServiceRequestModel request
          where request.clientDriver.driver.id = :driverId and request.status in :statuses
          """)
  Page<ServiceRequestModel> findPageByDriverIdAndStatusIn(
      Long driverId, Collection<ServiceRequestStatus> statuses, Pageable pageable);

  @Query(
      """
      select distinct request from ServiceRequestModel request
      join fetch request.items item
      join fetch item.days
      where request.id in :ids
      """)
  List<ServiceRequestModel> findWithItemsByIdIn(Collection<Long> ids);

  @Query(
      """
      select count(request) from ServiceRequestModel request
      where request.clientDriver.client.id = :clientId and request.status in :statuses
      """)
  long countByClientIdAndStatusIn(Long clientId, Collection<ServiceRequestStatus> statuses);

  @Query(
      """
      select request from ServiceRequestModel request
      where request.clientDriver.id = :clientDriverId and request.status in :statuses
      """)
  Optional<ServiceRequestModel> findByClientDriverIdAndStatusIn(
      Long clientDriverId, Collection<ServiceRequestStatus> statuses);

  @Query(
      """
      select request.id from ServiceRequestModel request
      where request.status = br.com.vanep.servicerequest.enums.ServiceRequestStatus.OPEN
        and request.expiresAt <= :now
      order by request.expiresAt, request.id
      """)
  List<Long> findOverdueOpenIds(Instant now);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      update ServiceRequestModel request
      set request.status = :to, request.updatedAt = instant
      where request.id = :id and request.status in :from
      """)
  int updateStatus(Long id, Collection<ServiceRequestStatus> from, ServiceRequestStatus to);
}
