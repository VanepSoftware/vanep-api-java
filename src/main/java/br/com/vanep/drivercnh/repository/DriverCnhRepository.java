package br.com.vanep.drivercnh.repository;

import br.com.vanep.drivercnh.model.DriverCnhModel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DriverCnhRepository extends JpaRepository<DriverCnhModel, Long> {

  Optional<DriverCnhModel> findByToken(String token);

  List<DriverCnhModel> findByDriverId(Long driverId);

  boolean existsByRegistrationNumber(String registrationNumber);

  boolean existsByDriverId(Long driverId);

  @Modifying
  @Query(value = "UPDATE driver_cnh SET deleted_at = NULL WHERE token = :token", nativeQuery = true)
  int restoreByToken(@Param("token") String token);

  @Query(
      value = "SELECT count(*) > 0 FROM driver_cnh WHERE token = :token AND deleted_at IS NOT NULL",
      nativeQuery = true)
  boolean existsDeletedByToken(@Param("token") String token);

  @Query(
      value =
          "SELECT u.token FROM driver_cnh c JOIN driver d ON c.driver_id = d.id JOIN users u ON d.user_id = u.id WHERE c.token = :token",
      nativeQuery = true)
  Optional<String> findDriverUserTokenByCnhToken(@Param("token") String token);
}
