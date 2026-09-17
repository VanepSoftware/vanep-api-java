package br.com.vanep.driver.dto;

import br.com.vanep.driver.DriverApprovalStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

public record DriverResponseDTO(
    String token,
    String name,
    String email,
    String phone,
    String document,
    String photo,
    BigDecimal rating,
    String bio,
    String cnpj,
    Integer experienceYears,
    BigDecimal basePrice,
    LocalTime workStartTime,
    LocalTime workEndTime,
    List<String> workDays,
    Integer waitToleranceMinutes,
    DriverApprovalStatus approvalStatus,
    boolean active,
    boolean available,
    Instant createdAt,
    Instant updatedAt) {}
