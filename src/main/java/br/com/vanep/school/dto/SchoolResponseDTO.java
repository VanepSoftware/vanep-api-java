package br.com.vanep.school.dto;

import br.com.vanep.address.dto.AddressResponseDTO;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record SchoolResponseDTO(
    String token,
    String name,
    @Nullable String googlePlaceId,
    @Nullable String cityName,
    @Nullable String cityToken,
    @Nullable AddressResponseDTO address,
    boolean active,
    Instant createdAt) {}
