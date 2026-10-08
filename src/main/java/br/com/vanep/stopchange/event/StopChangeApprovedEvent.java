package br.com.vanep.stopchange.event;

public record StopChangeApprovedEvent(
    String requestToken, Long dependentId, Long tripId, Long driverUserId) {}
