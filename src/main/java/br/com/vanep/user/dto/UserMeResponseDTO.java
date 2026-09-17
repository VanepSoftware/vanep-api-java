package br.com.vanep.user.dto;

import br.com.vanep.user.enums.Gender;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserMeResponseDTO(
    String token,
    String name,
    String phone,
    String email,
    String document,
    LocalDate birthDate,
    Gender gender,
    String type,
    String pendingEmail,
    Instant nameChangeAvailableAt,
    Instant phoneChangeAvailableAt,
    Instant emailChangeAvailableAt,
    OnboardingResponseDTO onboarding) {}
