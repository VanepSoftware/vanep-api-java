package br.com.vanep.proposal.event;

public sealed interface ProposalEvent {

  String proposalToken();

  record Sent(String proposalToken, String clientUid) implements ProposalEvent {}

  record AdjustmentRequested(String proposalToken, String driverUid) implements ProposalEvent {}

  record Accepted(String proposalToken, String driverUid) implements ProposalEvent {}

  record Declined(String proposalToken, String driverUid) implements ProposalEvent {}
}
