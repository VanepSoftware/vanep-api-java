package br.com.vanep.school.dto;

import br.com.vanep.address.dto.AddressRequestDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SchoolRequestDTO(
    @NotBlank(message = "{school.name.blank}") @Size(max = 255, message = "{school.name.too_long}")
        String name,
    @Valid AddressRequestDTO address) {}
