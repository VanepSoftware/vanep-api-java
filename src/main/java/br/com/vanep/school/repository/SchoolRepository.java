package br.com.vanep.school.repository;

import br.com.vanep.school.model.SchoolModel;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SchoolRepository extends JpaRepository<SchoolModel, Long> {
  Optional<SchoolModel> findByToken(String token);

  Optional<SchoolModel> findByGooglePlaceId(String googlePlaceId);

  boolean existsByName(String name);

  long countByAddressId(Long addressId);

  long countByAddressIdAndIdNot(Long addressId, Long id);

  @Modifying
  @Query(value = "UPDATE school SET deleted_at = NULL WHERE token = :token", nativeQuery = true)
  int restoreByToken(@Param("token") String token);

  @Query(
      value = "SELECT count(*) > 0 FROM school WHERE token = :token AND deleted_at IS NOT NULL",
      nativeQuery = true)
  boolean existsDeletedByToken(@Param("token") String token);
}
