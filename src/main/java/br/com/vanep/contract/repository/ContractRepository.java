package br.com.vanep.contract.repository;

import br.com.vanep.contract.enums.ContractStatus;
import br.com.vanep.contract.model.ContractModel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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
}
