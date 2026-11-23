package br.com.vanep.dependent.dto;

import br.com.vanep.address.dto.AddressResponseDTO;
import br.com.vanep.user.enums.Gender;
import java.time.Instant;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record DependentResponseDTO(
    String token,
    DependentClientDTO client,
    String name,
    LocalDate birthDate,
    Gender gender,
    @Nullable String document,
    String phone,
    String email,
    boolean isSelf,
    boolean isDefault,
    @Nullable DependentSchoolDTO school,
    @Nullable AddressResponseDTO address,
    Instant createdAt,
    Instant updatedAt) {}
