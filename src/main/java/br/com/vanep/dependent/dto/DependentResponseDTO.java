package br.com.vanep.dependent.dto;

import br.com.vanep.dependent.enums.Shift;
import br.com.vanep.user.Gender;
import java.time.Instant;
import java.time.LocalDate;

public record DependentResponseDTO(
    String token,
    Long clientId,
    String name,
    LocalDate birthDate,
    Gender gender,
    String document,
    String phone,
    String email,
    boolean isSelf,
    boolean isDefault,
    Shift shift,
    Long schoolId,
    Long addressId,
    Instant createdAt,
    Instant updatedAt) {}
