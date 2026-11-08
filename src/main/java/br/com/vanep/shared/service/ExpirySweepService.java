package br.com.vanep.shared.service;

import br.com.vanep.proposal.service.ProposalService;
import br.com.vanep.servicerequest.service.ServiceRequestService;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ExpirySweepService {

  private static final Logger log = LoggerFactory.getLogger(ExpirySweepService.class);

  private final Clock clock;
  private final ProposalService proposals;
  private final ServiceRequestService requests;
  private final int batchSize;

  public ExpirySweepService(
      Clock clock,
      ProposalService proposals,
      ServiceRequestService requests,
      @Value("${vanep.expiry-sweep.batch-size}") int batchSize) {
    this.clock = clock;
    this.proposals = proposals;
    this.requests = requests;
    this.batchSize = batchSize;
  }

  // Proposals first: an expired proposal reopens its request with a new deadline instead of
  // letting it expire in the same round.
  @Scheduled(fixedDelayString = "${vanep.expiry-sweep.interval}")
  public void expireOverdue() {
    Instant now = clock.instant();
    int expiredProposals = proposals.expireOverdue(now, batchSize);
    int expiredRequests = requests.expireOverdue(now, batchSize);
    if (expiredProposals + expiredRequests > 0) {
      log.info(
          "Expiry sweep: {} proposals and {} service requests expired.",
          expiredProposals,
          expiredRequests);
    }
  }
}
