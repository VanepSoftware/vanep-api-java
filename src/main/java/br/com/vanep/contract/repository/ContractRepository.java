package br.com.vanep.contract.repository;

import br.com.vanep.contract.enums.ContractStatus;
import br.com.vanep.contract.model.ContractModel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContractRepository extends JpaRepository<ContractModel, Long> {
  @Query(
      """
      select contract from ContractModel contract
      join fetch contract.clientDriver link
      join fetch link.client client
      join fetch client.user
      join fetch link.driver driver
      join fetch driver.user
      where contract.token = :token
      """)
  Optional<ContractModel> findByToken(String token);

  @Query(
      """
      select contract from ContractModel contract
      join fetch contract.clientDriver link
      join fetch link.client client
      join fetch client.user
      join fetch link.driver driver
      join fetch driver.user
      where link.id = :clientDriverId
      order by contract.startsOn desc, contract.id desc
      """)
  List<ContractModel> findByClientDriverId(Long clientDriverId);

  @Query(
      """
      select contract from ContractModel contract
      where contract.clientDriver.id = :clientDriverId
        and contract.status = br.com.vanep.contract.enums.ContractStatus.ACTIVE
      """)
  Optional<ContractModel> findActiveByClientDriverId(Long clientDriverId);

  @Query(
      """
      select contract.status from ContractModel contract
      where contract.clientDriver.id = :clientDriverId
      """)
  List<ContractStatus> findStatusesByClientDriverId(Long clientDriverId);

  @Query(
      "select contract.clientDriver.token from ContractModel contract where contract.token = :token")
  Optional<String> findClientDriverTokenByToken(String token);

  @Query(
      value =
          """
          select contract from ContractModel contract
          join fetch contract.clientDriver link
          join fetch link.client client
          join fetch client.user
          join fetch link.driver driver
          join fetch driver.user
          """,
      countQuery = "select count(contract) from ContractModel contract")
  Page<ContractModel> findPage(Pageable pageable);

  @Query(
      value = "SELECT count(*) > 0 FROM contract WHERE token = :token AND deleted_at IS NOT NULL",
      nativeQuery = true)
  boolean existsDeletedByToken(@Param("token") String token);

  @Query(
      value =
          """
          SELECT count(*) > 0 FROM contract deleted
          JOIN contract active ON active.client_driver_id = deleted.client_driver_id
          WHERE deleted.token = :token
            AND deleted.deleted_at IS NOT NULL
            AND deleted.status = 'ACTIVE'
            AND active.deleted_at IS NULL
            AND active.status = 'ACTIVE'
          """,
      nativeQuery = true)
  boolean existsActiveConflictForRestore(@Param("token") String token);

  @Modifying
  @Query(value = "UPDATE contract SET deleted_at = NULL WHERE token = :token", nativeQuery = true)
  int restoreByToken(@Param("token") String token);
}
