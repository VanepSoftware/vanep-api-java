package br.com.vanep.media.dto;

import br.com.vanep.media.enums.MediaPurpose;
import br.com.vanep.media.enums.MediaVisibility;
import java.time.Instant;

public record MediaFileResponseDTO(
    String token,
    String downloadUrl,
    MediaPurpose purpose,
    MediaVisibility visibility,
    String mimeType,
    Long sizeBytes,
    String originalName,
    Instant createdAt) {}
