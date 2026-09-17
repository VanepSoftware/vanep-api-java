package br.com.vanep.driverdocument.repository;

import br.com.vanep.driverdocument.enums.DocumentStatusEnum;
import br.com.vanep.driverdocument.enums.DocumentTypeEnum;
import br.com.vanep.driverdocument.model.DriverDocumentModel;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DriverDocumentRepository
    extends JpaRepository<DriverDocumentModel, Long>,
        JpaSpecificationExecutor<DriverDocumentModel> {

  Optional<DriverDocumentModel> findByToken(String token);

  Page<DriverDocumentModel> findByDriverId(Long driverId, Pageable pageable);

  Page<DriverDocumentModel> findByDriverIdAndDocumentType(
      Long driverId, DocumentTypeEnum documentType, Pageable pageable);

  Page<DriverDocumentModel> findByDriverIdAndStatus(
      Long driverId, DocumentStatusEnum status, Pageable pageable);

  @Modifying
  @Query(
      value = "UPDATE driver_document SET deleted_at = NULL WHERE token = :token",
      nativeQuery = true)
  int restoreByToken(@Param("token") String token);

  @Query(
      value =
          "SELECT count(*) > 0 FROM driver_document WHERE token = :token AND deleted_at IS NOT NULL",
      nativeQuery = true)
  boolean existsDeletedByToken(@Param("token") String token);

  @Query(
      value =
          "SELECT u.token FROM driver_document dd JOIN driver d ON dd.driver_id = d.id JOIN users u ON d.user_id = u.id WHERE dd.token = :token",
      nativeQuery = true)
  Optional<String> findDriverUserTokenByDocumentToken(@Param("token") String token);
}
