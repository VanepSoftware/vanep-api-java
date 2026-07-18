package br.com.vanep.school.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SchoolRequestDTO(
    @NotBlank(message = "O nome da escola é obrigatório.")
        @Size(max = 255, message = "O nome da escola deve ter no máximo 255 caracteres.")
        String name,
    @Pattern(regexp = "^\\d{14}$", message = "CNPJ em formato inválido (14 dígitos).") String cnpj,
    @Size(max = 32, message = "O telefone deve ter no máximo 32 caracteres.") String phone,
    @Email(message = "E-mail em formato inválido.")
        @Size(max = 255, message = "O e-mail deve ter no máximo 255 caracteres.")
        String email,
    Long addressId) {}
