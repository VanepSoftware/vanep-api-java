package br.com.vanep.driver;

import br.com.vanep.driver.model.DriverModel;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DriverRepository extends JpaRepository<DriverModel, Long> {

  Optional<DriverModel> findByUserId(Long userId);

  Optional<DriverModel> findByToken(String token);

  /**
   * Motoristas por id, já com o usuário carregado. O fetch join existe para a busca: sem ele, ler o
   * nome de cada motorista da página dispararia uma query por linha (regra 17).
   */
  @Query(
      value =
          """
          select distinct driver from DriverModel driver
          join fetch driver.user
          where driver.id in :ids and driver.active = true
          """,
      countQuery =
          "select count(driver) from DriverModel driver where driver.id in :ids and driver.active = true")
  Page<DriverModel> findActiveByIds(@Param("ids") Collection<Long> ids, Pageable pageable);

  @Query("select d.user.token from DriverModel d where d.token = :token")
  Optional<String> findUserTokenByDriverToken(@Param("token") String token);

  @Modifying
  @Query(value = "UPDATE driver SET deleted_at = NULL WHERE token = :token", nativeQuery = true)
  int restoreByToken(@Param("token") String token);

  @Query(
      value = "SELECT count(*) > 0 FROM driver WHERE token = :token AND deleted_at IS NOT NULL",
      nativeQuery = true)
  boolean existsDeletedByToken(@Param("token") String token);
}
