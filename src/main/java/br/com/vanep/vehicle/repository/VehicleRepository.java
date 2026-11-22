package br.com.vanep.vehicle.repository;

import br.com.vanep.vehicle.model.VehicleModel;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VehicleRepository extends JpaRepository<VehicleModel, Long> {

  Optional<VehicleModel> findByToken(String token);

  // Each vehicle has its own three photos, so they are fetched with the list (rule 17).
  @EntityGraph(attributePaths = {"driver.user", "photoFront", "photoSide"})
  List<VehicleModel> findByDriverId(Long driverId);

  @EntityGraph(attributePaths = {"driver.user", "photoFront", "photoSide"})
  List<VehicleModel> findByDriverIdInAndActiveTrueOrderByCreatedAtAscIdAsc(
      Collection<Long> driverIds);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select vehicle from VehicleModel vehicle where vehicle.id = :id")
  Optional<VehicleModel> lockById(@Param("id") @Nullable Long id);

  boolean existsByPlate(String plate);

  @Query(
      """
      select vehicle from VehicleModel vehicle
      left join fetch vehicle.photoFront
      left join fetch vehicle.photoSide
      where vehicle.driver.id = :driverId
        and vehicle.active = true
      order by vehicle.createdAt, vehicle.id
      """)
  List<VehicleModel> findActiveWithPhotosByDriverId(@Param("driverId") Long driverId);

  @Query(
      """
      select count(vehicle) > 0 from VehicleModel vehicle
      where vehicle.token = :token
        and vehicle.active = true
        and vehicle.driver.active = true
        and vehicle.driver.approvalStatus = br.com.vanep.driver.DriverApprovalStatus.APPROVED
      """)
  boolean existsOfSearchableDriverByToken(@Param("token") String token);

  @Modifying
  @Query(value = "UPDATE vehicle SET deleted_at = NULL WHERE token = :token", nativeQuery = true)
  int restoreByToken(@Param("token") String token);

  @Query(
      value = "SELECT count(*) > 0 FROM vehicle WHERE token = :token AND deleted_at IS NOT NULL",
      nativeQuery = true)
  boolean existsDeletedByToken(@Param("token") String token);

  @Query(
      value =
          "SELECT u.token FROM vehicle v JOIN driver d ON v.driver_id = d.id JOIN users u ON d.user_id = u.id WHERE v.token = :token",
      nativeQuery = true)
  Optional<String> findDriverUserTokenByVehicleToken(@Param("token") String token);

  // Native, so it also finds removed vehicles: the proposals that offered them outlive them.
  @Query(value = "SELECT id, token FROM vehicle WHERE id IN (:ids)", nativeQuery = true)
  List<IdAndToken> findTokensByIdIn(@Param("ids") Collection<Long> ids);

  @Query(
      """
      select vehicle.driver.id as driverId, count(vehicle) as total from VehicleModel vehicle
      where vehicle.driver.id in :driverIds and vehicle.active = true
      group by vehicle.driver.id
      """)
  List<DriverVehicleCount> countActiveByDriverIdIn(@Param("driverIds") Collection<Long> driverIds);

  interface DriverVehicleCount {
    Long getDriverId();

    Long getTotal();
  }

  interface IdAndToken {
    Long getId();

    String getToken();
  }
}
