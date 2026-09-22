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

  String FETCH_PARTIES =
      "join fetch rating.link link "
          + "join fetch link.client client join fetch client.user "
          + "join fetch link.driver driver join fetch driver.user ";

  Optional<DriverRatingModel> findByToken(String token);

  @Query(
      value = "select rating from DriverRatingModel rating " + FETCH_PARTIES,
      countQuery = "select count(rating) from DriverRatingModel rating")
  Page<DriverRatingModel> findPage(Pageable pageable);

  @Query(
      value =
          "select rating from DriverRatingModel rating "
              + FETCH_PARTIES
              + "where driver.token = :driverToken",
      countQuery =
          "select count(rating) from DriverRatingModel rating "
              + "where rating.link.driver.token = :driverToken")
  Page<DriverRatingModel> findByDriverToken(
      @Param("driverToken") String driverToken, Pageable pageable);

  boolean existsByLinkId(Long linkId);

  @Query("SELECT AVG(dr.rating) FROM DriverRatingModel dr WHERE dr.link.driver.id = :driverId")
  Optional<BigDecimal> calculateAverageRatingForDriver(@Param("driverId") Long driverId);

  @Query(
      "SELECT u.token FROM DriverRatingModel dr JOIN dr.link l JOIN l.client c JOIN c.user u WHERE dr.token = :token")
  Optional<String> findClientUserTokenByRatingToken(@Param("token") String token);
}
