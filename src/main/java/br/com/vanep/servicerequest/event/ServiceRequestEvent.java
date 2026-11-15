package br.com.vanep.servicerequest.event;

public sealed interface ServiceRequestEvent {

  String requestToken();

  record Sent(String requestToken, String driverUid) implements ServiceRequestEvent {}

  record Declined(String requestToken, String clientUid) implements ServiceRequestEvent {}
}
