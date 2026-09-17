package br.com.vanep.clientrating.repository;

import br.com.vanep.clientrating.model.ClientRatingModel;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClientRatingRepository extends JpaRepository<ClientRatingModel, Long> {

  Optional<ClientRatingModel> findByToken(String token);

  Page<ClientRatingModel> findByClientToken(String clientToken, Pageable pageable);

  boolean existsByDriverIdAndClientId(Long driverId, Long clientId);

  @Query("SELECT AVG(cr.rating) FROM ClientRatingModel cr WHERE cr.client.id = :clientId")
  Optional<BigDecimal> calculateAverageRatingForClient(@Param("clientId") Long clientId);

  @Query(
      "SELECT u.token FROM ClientRatingModel cr JOIN cr.driver d JOIN d.user u WHERE cr.token = :token")
  Optional<String> findDriverUserTokenByRatingToken(@Param("token") String token);

  @Modifying
  @Query(
      value = "UPDATE client_rating SET deleted_at = NULL WHERE token = :token",
      nativeQuery = true)
  int restoreByToken(@Param("token") String token);

  @Query(
      value =
          "SELECT count(*) > 0 FROM client_rating WHERE token = :token AND deleted_at IS NOT NULL",
      nativeQuery = true)
  boolean existsDeletedByToken(@Param("token") String token);
}
