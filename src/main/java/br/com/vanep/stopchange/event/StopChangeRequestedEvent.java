package br.com.vanep.stopchange.event;

public record StopChangeRequestedEvent(
    String requestToken, Long dependentId, Long tripId, Long requestedByUserId) {}
