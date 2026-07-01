package br.com.vanep.dependent.repository;

import br.com.vanep.dependent.entity.DependentEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DependentRepository extends JpaRepository<DependentEntity, Long> {

  Optional<DependentEntity> findByToken(String token);

  List<DependentEntity> findByClientId(Long clientId);

  long countByClientId(Long clientId);

  boolean existsByDocument(String document);

  boolean existsByDocumentAndTokenNot(String document, String token);

  @Query(
      value = "SELECT client_id FROM dependent WHERE token = :token AND deleted_at IS NOT NULL",
      nativeQuery = true)
  Optional<Long> findClientIdOfDeletedByToken(@Param("token") String token);

  @Modifying
  @Query(value = "UPDATE dependent SET deleted_at = NULL WHERE token = :token", nativeQuery = true)
  int restoreByToken(@Param("token") String token);

  @Query(
      value = "SELECT count(*) > 0 FROM dependent WHERE token = :token AND deleted_at IS NOT NULL",
      nativeQuery = true)
  boolean existsDeletedByToken(@Param("token") String token);
}
