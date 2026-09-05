package br.com.vanep.school.dto;

import br.com.vanep.address.dto.AddressResponseDTO;
import java.time.Instant;

public record SchoolResponseDTO(
    String token,
    String name,
    String googlePlaceId,
    String cityName,
    String cityToken,
    String districtName,
    String districtToken,
    AddressResponseDTO address,
    boolean active,
    Instant createdAt) {}
