package br.com.vanep.proposal.repository;

import br.com.vanep.proposal.enums.ProposalStatus;
import br.com.vanep.proposal.model.ProposalModel;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ProposalRepository extends JpaRepository<ProposalModel, Long> {
  @Query(
      """
      select distinct proposal from ProposalModel proposal
      join fetch proposal.clientDriver link
      join fetch link.client client
      join fetch client.user
      join fetch link.driver driver
      join fetch driver.user
      join fetch proposal.serviceRequest
      join fetch proposal.items item
      join fetch item.schedule schedule
      left join fetch schedule.slots
      where proposal.token = :token and client.id = :clientId
      """)
  Optional<ProposalModel> findByTokenAndClientId(String token, Long clientId);

  @Query(
      """
      select distinct proposal from ProposalModel proposal
      join fetch proposal.clientDriver link
      join fetch link.client client
      join fetch client.user
      join fetch link.driver driver
      join fetch driver.user
      join fetch proposal.serviceRequest
      join fetch proposal.items item
      join fetch item.schedule schedule
      left join fetch schedule.slots
      where proposal.token = :token and driver.id = :driverId
      """)
  Optional<ProposalModel> findByTokenAndDriverId(String token, Long driverId);

  @Query(
      """
      select distinct proposal from ProposalModel proposal
      join fetch proposal.items item
      join fetch item.schedule schedule
      left join fetch schedule.slots
      where proposal.serviceRequest.id = :serviceRequestId
      order by proposal.version
      """)
  List<ProposalModel> findChainByServiceRequestId(Long serviceRequestId);

  @Query(
      """
      select proposal from ProposalModel proposal
      where proposal.clientDriver.id = :clientDriverId and proposal.status = :status
      """)
  Optional<ProposalModel> findByClientDriverIdAndStatus(Long clientDriverId, ProposalStatus status);

  @Query(
      """
      select proposal.id from ProposalModel proposal
      where proposal.status = br.com.vanep.proposal.enums.ProposalStatus.SENT
        and proposal.validUntil <= :now
      order by proposal.validUntil, proposal.id
      """)
  List<Long> findOverdueSentIds(Instant now);

  @Modifying
  @Query(
      """
      update ProposalModel proposal
      set proposal.status = :to, proposal.updatedAt = instant
      where proposal.id = :id and proposal.status = :from
      """)
  int updateStatus(Long id, ProposalStatus from, ProposalStatus to);
}
