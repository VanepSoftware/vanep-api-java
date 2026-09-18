package br.com.vanep.driverrating.repository;

import br.com.vanep.driverrating.model.DriverRatingModel;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DriverRatingRepository extends JpaRepository<DriverRatingModel, Long> {

  Optional<DriverRatingModel> findByToken(String token);

  Page<DriverRatingModel> findByDriverToken(String driverToken, Pageable pageable);

  boolean existsByDriverIdAndClientId(Long driverId, Long clientId);

  @Query("SELECT AVG(dr.rating) FROM DriverRatingModel dr WHERE dr.driver.id = :driverId")
  Optional<BigDecimal> calculateAverageRatingForDriver(@Param("driverId") Long driverId);

  @Query(
      "SELECT u.token FROM DriverRatingModel dr JOIN dr.client c JOIN c.user u WHERE dr.token = :token")
  Optional<String> findClientUserTokenByRatingToken(@Param("token") String token);
}
