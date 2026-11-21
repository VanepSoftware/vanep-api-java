package br.com.vanep.driver.dto;

import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.user.dto.UserMeResponseDTO;
import java.math.BigDecimal;

public record DriverMeSummaryResponseDTO(
    String token,
    String photo,
    BigDecimal rating,
    long ratingCount,
    DriverApprovalStatus approvalStatus,
    boolean available,
    boolean active,
    BigDecimal basePrice,
    Integer experienceYears,
    UserMeResponseDTO user) {}
