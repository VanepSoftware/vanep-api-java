package br.com.vanep.stopchange.event;

public record StopChangeRejectedEvent(
    String requestToken, Long dependentId, Long tripId, Long driverUserId) {}
