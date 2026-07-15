package br.com.vanep.client.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

// ALTERADO (painel admin #11): antes o record só tinha (photo, addressToken).
// Foram adicionados name, email, rating e active para o admin editar todos os
// campos do cliente pelo painel (menos CPF e senha). Cada campo ganhou validação.
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
