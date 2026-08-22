package br.com.vanep.school.dto;

import br.com.vanep.address.dto.AddressResponseDTO;
import java.time.Instant;

public record SchoolResponseDTO(
    String token,
    String name,
    String cnpj,
    String phone,
    String email,
    AddressResponseDTO address,
    boolean active,
    Instant createdAt) {}
