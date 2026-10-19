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

  List<DependentModel> findByClientId(Long clientId);

  List<DependentModel> findByTokenInAndClientId(Collection<String> tokens, Long clientId);

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
