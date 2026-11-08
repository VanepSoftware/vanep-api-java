package br.com.vanep.proposal.service;

import br.com.vanep.proposal.enums.ProposalStatus;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ProposalTransitionPolicy {

  public boolean allows(ProposalStatus from, ProposalStatus to) {
    return nextStatusesOf(from).contains(to);
  }

  Set<ProposalStatus> nextStatusesOf(ProposalStatus from) {
    return switch (from) {
      case SENT ->
          Set.of(
              ProposalStatus.ACCEPTED,
              ProposalStatus.SUPERSEDED,
              ProposalStatus.DECLINED,
              ProposalStatus.EXPIRED,
              ProposalStatus.WITHDRAWN);
      case ACCEPTED, SUPERSEDED, DECLINED, EXPIRED, WITHDRAWN -> Set.of();
    };
  }
}
