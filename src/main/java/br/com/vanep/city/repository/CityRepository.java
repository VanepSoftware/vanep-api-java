package br.com.vanep.city.repository;

import br.com.vanep.city.model.CityModel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CityRepository extends JpaRepository<CityModel, Long> {

  Optional<CityModel> findByToken(String token);

  Optional<CityModel> findFirstByNameIgnoreCase(String name);

  List<CityModel> findByStateId(Long stateId);

  Optional<CityModel> findByStateIdAndNormalizedName(Long stateId, String normalizedName);

  Optional<CityModel> findByGooglePlaceId(String googlePlaceId);

  boolean existsByNameIgnoreCaseAndStateId(String name, Long stateId);

  @Modifying
  @Query(value = "UPDATE city SET deleted_at = NULL WHERE token = :token", nativeQuery = true)
  int restoreByToken(@Param("token") String token);

  @Query(
      value = "SELECT count(*) > 0 FROM city WHERE token = :token AND deleted_at IS NOT NULL",
      nativeQuery = true)
  boolean existsDeletedByToken(@Param("token") String token);
}
