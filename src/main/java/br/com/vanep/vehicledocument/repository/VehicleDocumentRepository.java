package br.com.vanep.vehicledocument.repository;

import br.com.vanep.shared.document.enums.DocumentStatus;
import br.com.vanep.vehicledocument.enums.VehicleDocumentType;
import br.com.vanep.vehicledocument.model.VehicleDocumentModel;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VehicleDocumentRepository extends JpaRepository<VehicleDocumentModel, Long> {

  @Query(
      """
      select document from VehicleDocumentModel document
      join fetch document.vehicle vehicle
      join fetch vehicle.driver
      where document.token = :token
      """)
  Optional<VehicleDocumentModel> findByToken(@Param("token") String token);

  @Query(
      value =
          """
          select document from VehicleDocumentModel document
          join document.vehicle vehicle
          where vehicle.token = :vehicleToken
            and (:documentType is null or document.documentType = :documentType)
            and (:status is null or document.review.status = :status)
          order by document.expiresAt desc nulls first, document.createdAt desc, document.id desc
          """,
      countQuery =
          """
          select count(document) from VehicleDocumentModel document
          join document.vehicle vehicle
          where vehicle.token = :vehicleToken
            and (:documentType is null or document.documentType = :documentType)
            and (:status is null or document.review.status = :status)
          """)
  Page<VehicleDocumentModel> findByVehicleToken(
      @Param("vehicleToken") String vehicleToken,
      @Param("documentType") VehicleDocumentType documentType,
      @Param("status") DocumentStatus status,
      Pageable pageable);

  @Query(
      """
      select document from VehicleDocumentModel document
      join fetch document.vehicle vehicle
      where vehicle.id in :vehicleIds
      order by document.expiresAt desc nulls first, document.createdAt desc, document.id desc
      """)
  List<VehicleDocumentModel> findByVehicleIdIn(@Param("vehicleIds") Collection<Long> vehicleIds);

  @Modifying
  @Query(
      value = "UPDATE vehicle_document SET deleted_at = NULL WHERE token = :token",
      nativeQuery = true)
  int restoreByToken(@Param("token") String token);

  @Query(
      value =
          """
          SELECT count(*) > 0 FROM vehicle_document document
          JOIN vehicle ON vehicle.id = document.vehicle_id
          WHERE document.token = :token
            AND document.deleted_at IS NOT NULL
            AND vehicle.deleted_at IS NULL
          """,
      nativeQuery = true)
  boolean existsDeletedByToken(@Param("token") String token);

  @Query(
      value =
          """
          SELECT u.token FROM vehicle_document document
          JOIN vehicle ON vehicle.id = document.vehicle_id
          JOIN driver ON driver.id = vehicle.driver_id
          JOIN users u ON u.id = driver.user_id
          WHERE document.token = :token AND vehicle.deleted_at IS NULL
          """,
      nativeQuery = true)
  Optional<String> findDriverUserTokenByDocumentToken(@Param("token") String token);
}
