package br.com.vanep.clientdriver.dto;

import br.com.vanep.clientdriver.enums.LinkOrigin;
import br.com.vanep.clientdriver.enums.RelationshipStatus;
import java.time.Instant;

public record ClientDriverResponseDTO(
    String token,
    String clientToken,
    String clientName,
    String driverToken,
    String driverName,
    RelationshipStatus status,
    LinkOrigin origin,
    Instant createdAt,
    Instant updatedAt) {}
