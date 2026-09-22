package br.com.vanep.clientrating.repository;

import br.com.vanep.clientrating.model.ClientRatingModel;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClientRatingRepository extends JpaRepository<ClientRatingModel, Long> {

  String FETCH_PARTIES =
      "join fetch rating.link link "
          + "join fetch link.client client join fetch client.user "
          + "join fetch link.driver driver join fetch driver.user ";

  Optional<ClientRatingModel> findByToken(String token);

  @Query(
      value = "select rating from ClientRatingModel rating " + FETCH_PARTIES,
      countQuery = "select count(rating) from ClientRatingModel rating")
  Page<ClientRatingModel> findPage(Pageable pageable);

  @Query(
      value =
          "select rating from ClientRatingModel rating "
              + FETCH_PARTIES
              + "where client.token = :clientToken",
      countQuery =
          "select count(rating) from ClientRatingModel rating "
              + "where rating.link.client.token = :clientToken")
  Page<ClientRatingModel> findByClientToken(
      @Param("clientToken") String clientToken, Pageable pageable);

  boolean existsByLinkId(Long linkId);

  @Query("SELECT AVG(cr.rating) FROM ClientRatingModel cr WHERE cr.link.client.id = :clientId")
  Optional<BigDecimal> calculateAverageRatingForClient(@Param("clientId") Long clientId);

  @Query(
      "SELECT u.token FROM ClientRatingModel cr JOIN cr.link l JOIN l.driver d JOIN d.user u WHERE cr.token = :token")
  Optional<String> findDriverUserTokenByRatingToken(@Param("token") String token);
}
