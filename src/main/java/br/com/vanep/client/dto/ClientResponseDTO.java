package br.com.vanep.client.dto;

import br.com.vanep.address.dto.AddressResponseDTO;
import java.math.BigDecimal;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record ClientResponseDTO(
    String token,
    String name,
    String email,
    @Nullable String photo,
    BigDecimal rating,
    @Nullable AddressResponseDTO address,
    boolean active,
    Instant createdAt) {}
