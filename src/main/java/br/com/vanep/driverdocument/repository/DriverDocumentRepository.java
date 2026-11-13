package br.com.vanep.driverdocument.repository;

import br.com.vanep.driverdocument.enums.DriverDocumentType;
import br.com.vanep.driverdocument.model.DriverDocumentModel;
import br.com.vanep.shared.document.enums.DocumentStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DriverDocumentRepository
    extends JpaRepository<DriverDocumentModel, Long>,
        JpaSpecificationExecutor<DriverDocumentModel> {

  Optional<DriverDocumentModel> findByToken(String token);

  // Each document has its own file and reviewer (and, on the admin list, its own driver), so
  // every list fetches them with the rows instead of one select per document (rule 17).
  @EntityGraph(attributePaths = {"driver.user", "file", "review.reviewedBy"})
  List<DriverDocumentModel> findByDriverId(Long driverId);

  @Query(
      """
      select document from DriverDocumentModel document
      join fetch document.driver driver
      join fetch driver.user
      left join fetch document.file
      left join fetch document.review.reviewedBy
      where driver.id = :driverId
      """)
  List<DriverDocumentModel> findWithDetailsByDriverId(@Param("driverId") Long driverId);

  @EntityGraph(attributePaths = {"driver.user", "file", "review.reviewedBy"})
  Page<DriverDocumentModel> findByDriverId(Long driverId, Pageable pageable);

  // The list: every filter is optional, and the ones sent apply together.
  @EntityGraph(attributePaths = {"driver.user", "file", "review.reviewedBy"})
  @Query(
      """
      select document from DriverDocumentModel document
      where (:driverId is null or document.driver.id = :driverId)
        and (:documentType is null or document.documentType = :documentType)
        and (:status is null or document.review.status = :status)
      """)
  Page<DriverDocumentModel> findFiltered(
      @Param("driverId") Long driverId,
      @Param("documentType") DriverDocumentType documentType,
      @Param("status") DocumentStatus status,
      Pageable pageable);

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
