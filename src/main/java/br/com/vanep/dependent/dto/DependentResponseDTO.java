package br.com.vanep.dependent.dto;

import br.com.vanep.address.dto.AddressResponseDTO;
import br.com.vanep.shared.enums.SchoolShift;
import br.com.vanep.user.enums.Gender;
import java.time.Instant;
import java.time.LocalDate;

public record DependentResponseDTO(
    String token,
    DependentClientDTO client,
    String name,
    LocalDate birthDate,
    Gender gender,
    String document,
    String phone,
    String email,
    boolean isSelf,
    boolean isDefault,
    SchoolShift shift,
    DependentSchoolDTO school,
    AddressResponseDTO address,
    Instant createdAt,
    Instant updatedAt) {}
