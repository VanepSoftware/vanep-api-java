package br.com.vanep.country.repository;

import br.com.vanep.country.model.CountryModel;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CountryRepository extends JpaRepository<CountryModel, Long> {

  Optional<CountryModel> findByToken(String token);

  Optional<CountryModel> findByName(String name);

  boolean existsByName(String name);

  boolean existsByIsoCode(String isoCode);

  @Modifying
  @Query(value = "UPDATE country SET deleted_at = NULL WHERE token = :token", nativeQuery = true)
  int restoreByToken(@Param("token") String token);

  @Query(
      value = "SELECT count(*) > 0 FROM country WHERE token = :token AND deleted_at IS NOT NULL",
      nativeQuery = true)
  boolean existsDeletedByToken(@Param("token") String token);
}
