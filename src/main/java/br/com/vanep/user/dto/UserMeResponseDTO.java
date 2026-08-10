package br.com.vanep.user.dto;

import br.com.vanep.user.Gender;
import java.time.LocalDate;

public record UserMeResponseDTO(
    String token,
    String name,
    String phone,
    String email,
    String document,
    LocalDate birthDate,
    Gender gender,
    String type) {}
