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

  @Query(
      value =
          """
          select distinct driver from DriverModel driver
          join fetch driver.user
          where driver.id in :ids
            and driver.active = true
            and driver.approvalStatus = br.com.vanep.driver.DriverApprovalStatus.APPROVED
          """,
      countQuery =
          """
          select count(driver) from DriverModel driver
          where driver.id in :ids
            and driver.active = true
            and driver.approvalStatus = br.com.vanep.driver.DriverApprovalStatus.APPROVED
          """)
  Page<DriverModel> findSearchableByIds(@Param("ids") Collection<Long> ids, Pageable pageable);

  @Query(
      value =
          """
          select driver from DriverModel driver
          join fetch driver.user
          where driver.active = true
            and driver.approvalStatus = br.com.vanep.driver.DriverApprovalStatus.APPROVED
          order by driver.createdAt desc, driver.id desc
          """,
      countQuery =
          """
          select count(driver) from DriverModel driver
          where driver.active = true
            and driver.approvalStatus = br.com.vanep.driver.DriverApprovalStatus.APPROVED
          """)
  Page<DriverModel> findSearchableNewestFirst(Pageable pageable);

  @Query(
      """
      select driver from DriverModel driver
      join fetch driver.user
      where driver.token = :token
        and driver.active = true
        and driver.approvalStatus = br.com.vanep.driver.DriverApprovalStatus.APPROVED
      """)
  Optional<DriverModel> findSearchableByToken(@Param("token") String token);

  @Query(
      """
      select count(driver) > 0 from DriverModel driver
      where driver.token = :token
        and driver.active = true
        and driver.approvalStatus = br.com.vanep.driver.DriverApprovalStatus.APPROVED
      """)
  boolean existsSearchableByToken(@Param("token") String token);

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
