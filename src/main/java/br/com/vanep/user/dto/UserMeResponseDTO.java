package br.com.vanep.user.dto;

import br.com.vanep.user.enums.Gender;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserMeResponseDTO(
    String token,
    String name,
    @Nullable String phone,
    String email,
    String document,
    LocalDate birthDate,
    Gender gender,
    String type,
    @Nullable String pendingEmail,
    @Nullable Instant nameChangeAvailableAt,
    @Nullable Instant phoneChangeAvailableAt,
    @Nullable Instant emailChangeAvailableAt,
    OnboardingResponseDTO onboarding) {}
