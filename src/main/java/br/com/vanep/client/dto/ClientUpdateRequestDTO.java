package br.com.vanep.client.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ClientUpdateRequestDTO(
    @Size(max = 255, message = "O nome deve ter no máximo 255 caracteres.") String name,
    @Email(message = "E-mail em formato inválido.")
        @Size(max = 255, message = "O e-mail deve ter no máximo 255 caracteres.")
        String email,
    String photo,
    String addressToken,
    @DecimalMin(value = "0.0", message = "A avaliação deve ser entre 0 e 5.")
        @DecimalMax(value = "5.0", message = "A avaliação deve ser entre 0 e 5.")
        BigDecimal rating,
    Boolean active) {}
