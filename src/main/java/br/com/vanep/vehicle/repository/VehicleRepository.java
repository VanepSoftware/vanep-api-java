package br.com.vanep.vehicle.repository;

import br.com.vanep.vehicle.model.VehicleModel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VehicleRepository extends JpaRepository<VehicleModel, Long> {

  Optional<VehicleModel> findByToken(String token);

  List<VehicleModel> findByDriverId(Long driverId);

  boolean existsByPlate(String plate);

  @Query(
      """
      select vehicle from VehicleModel vehicle
      left join fetch vehicle.photoFront
      left join fetch vehicle.photoSide
      left join fetch vehicle.photoDocument
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
}
