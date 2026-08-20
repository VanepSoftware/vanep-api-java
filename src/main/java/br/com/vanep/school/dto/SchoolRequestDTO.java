package br.com.vanep.school.dto;

import br.com.vanep.address.dto.AddressRequestDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SchoolRequestDTO(
    @NotBlank(message = "{school.name.blank}") @Size(max = 255, message = "{school.name.too_long}")
        String name,
    @Pattern(regexp = "^\\d{14}$", message = "{school.cnpj.invalid}") String cnpj,
    @Size(max = 32, message = "{school.phone.too_long}") String phone,
    @Email(message = "{school.email.invalid}") @Size(max = 255, message = "{school.email.too_long}")
        String email,
    @Valid AddressRequestDTO address) {}
