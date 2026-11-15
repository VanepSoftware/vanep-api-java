package br.com.vanep.shared.service;

import static org.mockito.Mockito.inOrder;

import br.com.vanep.proposal.service.ProposalService;
import br.com.vanep.servicerequest.service.ServiceRequestService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExpirySweepServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-24T12:00:00Z");

  @Mock private ProposalService proposals;
  @Mock private ServiceRequestService requests;

  @Test
  void expiresTheProposalsBeforeTheRequestsAtTheSameInstantAndBatchSize() {
    ExpirySweepService sweep =
        new ExpirySweepService(Clock.fixed(NOW, ZoneOffset.UTC), proposals, requests, 200);

    sweep.expireOverdue();

    InOrder order = inOrder(proposals, requests);
    order.verify(proposals).expireOverdue(NOW, 200);
    order.verify(requests).expireOverdue(NOW, 200);
  }
}
