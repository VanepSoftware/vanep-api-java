package br.com.vanep.unlinkedpassenger.dto;

import org.jspecify.annotations.Nullable;

public record UnlinkedPassengerFamilyKeyDTO(
    Long passengerId, @Nullable Long vehicleId, Long dependentId) {}
