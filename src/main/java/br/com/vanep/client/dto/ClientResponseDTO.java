package br.com.vanep.client.dto;

import br.com.vanep.address.dto.AddressResponseDTO;
import java.math.BigDecimal;
import java.time.Instant;

public record ClientResponseDTO(
    String token,
    String name,
    String email,
    String photo,
    BigDecimal rating,
    AddressResponseDTO address,
    boolean active,
    Instant createdAt) {}
