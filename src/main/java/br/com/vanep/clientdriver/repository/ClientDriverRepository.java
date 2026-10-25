package br.com.vanep.clientdriver.repository;

import br.com.vanep.clientdriver.model.ClientDriverModel;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClientDriverRepository extends JpaRepository<ClientDriverModel, Long> {
  @Query(
      """
      select link from ClientDriverModel link
      join fetch link.client client
      join fetch client.user
      join fetch link.driver driver
      join fetch driver.user
      where client.id = :clientId and driver.id = :driverId
      """)
  Optional<ClientDriverModel> findByPair(Long clientId, Long driverId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select link from ClientDriverModel link
      where link.client.id = :clientId and link.driver.id = :driverId
      """)
  Optional<ClientDriverModel> lockByPair(Long clientId, Long driverId);

  @Query(
      """
      select link from ClientDriverModel link
      join fetch link.client client
      join fetch client.user
      join fetch link.driver driver
      join fetch driver.user
      where link.token = :token
      """)
  Optional<ClientDriverModel> findByToken(String token);

  @Query(
      """
      select link from ClientDriverModel link
      join fetch link.client client
      join fetch client.user
      join fetch link.driver driver
      join fetch driver.user
      where client.user.id = :userId
      order by link.id
      """)
  List<ClientDriverModel> findByClientUserId(Long userId);

  @Query(
      """
      select link from ClientDriverModel link
      join fetch link.client client
      join fetch client.user
      join fetch link.driver driver
      join fetch driver.user
      where driver.user.id = :userId
      order by link.id
      """)
  List<ClientDriverModel> findByDriverUserId(Long userId);

  @Query(
      value =
          """
          select link from ClientDriverModel link
          join fetch link.client client
          join fetch client.user
          join fetch link.driver driver
          join fetch driver.user
          """,
      countQuery = "select count(link) from ClientDriverModel link")
  Page<ClientDriverModel> findPage(Pageable pageable);

  @Modifying
  @Query(
      value = "UPDATE client_driver SET deleted_at = NULL WHERE token = :token",
      nativeQuery = true)
  int restoreByToken(@Param("token") String token);

  @Query(
      value =
          "SELECT count(*) > 0 FROM client_driver WHERE token = :token AND deleted_at IS NOT NULL",
      nativeQuery = true)
  boolean existsDeletedByToken(@Param("token") String token);

  @Query(
      value =
          "SELECT u.token FROM client_driver cd JOIN client c ON cd.client_id = c.id JOIN users u ON c.user_id = u.id WHERE cd.token = :token",
      nativeQuery = true)
  Optional<String> findClientUserTokenByLinkToken(@Param("token") String token);

  @Query(
      value =
          "SELECT u.token FROM client_driver cd JOIN driver d ON cd.driver_id = d.id JOIN users u ON d.user_id = u.id WHERE cd.token = :token",
      nativeQuery = true)
  Optional<String> findDriverUserTokenByLinkToken(@Param("token") String token);

  // Native: an HQL assignment to blockedBy, whose target is soft-deletable, makes Hibernate 7
  // join the users table and write the parameter into the link's id.
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          """
          update client_driver
          set status = 'BLOCKED', blocked_by = :blockerId, blocked_at = :now,
            updated_at = current_timestamp
          where id = :id and status <> 'BLOCKED' and deleted_at is null
          """,
      nativeQuery = true)
  int block(@Param("id") Long id, @Param("blockerId") Long blockerId, @Param("now") Instant now);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          """
          update client_driver
          set status = :status, blocked_by = null, blocked_at = null,
            updated_at = current_timestamp
          where id = :id and status = 'BLOCKED' and blocked_by = :blockerId and deleted_at is null
          """,
      nativeQuery = true)
  int unblock(
      @Param("id") Long id, @Param("blockerId") Long blockerId, @Param("status") String status);
}
