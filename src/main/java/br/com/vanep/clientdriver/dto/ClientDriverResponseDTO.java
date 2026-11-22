package br.com.vanep.clientdriver.dto;

import br.com.vanep.clientdriver.enums.LinkOrigin;
import br.com.vanep.clientdriver.enums.LinkParty;
import br.com.vanep.clientdriver.enums.RelationshipStatus;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record ClientDriverResponseDTO(
    String token,
    String clientToken,
    String clientName,
    String driverToken,
    String driverName,
    RelationshipStatus status,
    LinkOrigin origin,
    @Nullable LinkParty blockedBy,
    Instant createdAt,
    Instant updatedAt) {}
