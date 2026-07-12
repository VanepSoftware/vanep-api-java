package br.com.vanep.assistant.dto;

import br.com.vanep.assistant.enums.AssistantStatus;
import br.com.vanep.assistant.enums.VerificationStatus;
import java.time.Instant;

public record AssistantProfileResponseDTO(
    String token,
    AssistantStatus status,
    String name,
    String photo,
    VerificationStatus verificationStatus,
    Instant activatedAt,
    Instant createdAt) {}
