package br.com.vanep.proposal.repository;

import br.com.vanep.proposal.model.ProposalAdjustmentModel;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProposalAdjustmentRepository extends JpaRepository<ProposalAdjustmentModel, Long> {
  List<ProposalAdjustmentModel> findByProposalIdInOrderByCreatedAtAscIdAsc(
      Collection<Long> proposalIds);
}
