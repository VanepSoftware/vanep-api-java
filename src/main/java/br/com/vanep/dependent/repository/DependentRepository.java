package br.com.vanep.dependent.repository;

import br.com.vanep.dependent.model.DependentModel;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DependentRepository extends JpaRepository<DependentModel, Long> {

  Optional<DependentModel> findByToken(String token);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select dependent from DependentModel dependent where dependent.token = :token")
  Optional<DependentModel> lockByToken(@Param("token") String token);

  // Locked in id order, so two writers locking overlapping dependents wait instead of deadlocking.
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select dependent from DependentModel dependent
      where dependent.token in :tokens and dependent.clientId = :clientId
      order by dependent.id
      """)
  List<DependentModel> lockByTokenInAndClientId(
      @Param("tokens") Collection<String> tokens, @Param("clientId") Long clientId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select dependent from DependentModel dependent
      where dependent.id in :ids
      order by dependent.id
      """)
  List<DependentModel> lockByIdIn(@Param("ids") Collection<Long> ids);

  List<DependentModel> findByClientId(Long clientId);

  List<DependentModel> findByTokenInAndClientId(Collection<String> tokens, Long clientId);

  @Query(
      value =
          """
          SELECT count(*) > 0 FROM service_request_item item
          JOIN service_request request ON request.id = item.service_request_id
          WHERE item.dependent_id = :dependentId AND request.status IN ('OPEN', 'ANSWERED')
          """,
      nativeQuery = true)
  boolean existsInOpenServiceRequest(@Param("dependentId") Long dependentId);

  @Query(
      value =
          """
          SELECT count(*) > 0 FROM contract_item item
          JOIN contract ON contract.id = item.contract_id
          WHERE item.dependent_id = :dependentId
            AND item.deleted_at IS NULL AND contract.deleted_at IS NULL
            AND contract.status IN ('AWAITING_SIGNATURES', 'SIGNED', 'ACTIVE', 'SUSPENDED')
          """,
      nativeQuery = true)
  boolean existsInOpenContract(@Param("dependentId") Long dependentId);

  // Native, so it also finds removed dependents: the requests that name them outlive them.
  @Query(value = "SELECT id, token FROM dependent WHERE id IN (:ids)", nativeQuery = true)
  List<IdAndToken> findTokensByIdIn(@Param("ids") Collection<Long> ids);

  interface IdAndToken {
    Long getId();

    String getToken();
  }

  long countByClientId(Long clientId);

  boolean existsByClientIdAndName(Long clientId, String name);

  boolean existsByDocument(String document);

  boolean existsByDocumentAndTokenNot(String document, String token);

  long countByAddressId(Long addressId);

  long countByAddressIdAndIdNot(Long addressId, Long id);

  @Query(
      value = "SELECT client_id FROM dependent WHERE token = :token AND deleted_at IS NOT NULL",
      nativeQuery = true)
  Optional<Long> findClientIdOfDeletedByToken(@Param("token") String token);

  @Modifying(flushAutomatically = true)
  @Query(
      value =
          "UPDATE dependent SET is_default = false WHERE client_id = :clientId"
              + " AND is_default AND deleted_at IS NULL"
              + " AND EXISTS (SELECT 1 FROM dependent WHERE token = :token"
              + " AND is_default AND deleted_at IS NOT NULL)",
      nativeQuery = true)
  int clearActiveDefaultsIfDeletedIsDefault(
      @Param("clientId") Long clientId, @Param("token") String token);

  @Modifying
  @Query(value = "UPDATE dependent SET deleted_at = NULL WHERE token = :token", nativeQuery = true)
  int restoreByToken(@Param("token") String token);

  @Query(
      value = "SELECT count(*) > 0 FROM dependent WHERE token = :token AND deleted_at IS NOT NULL",
      nativeQuery = true)
  boolean existsDeletedByToken(@Param("token") String token);
}
