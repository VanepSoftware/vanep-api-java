package br.com.vanep.driver.dto;

import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.user.dto.UserMeResponseDTO;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

public record DriverMeSummaryResponseDTO(
    String token,
    @Nullable String photo,
    @Nullable BigDecimal rating,
    DriverApprovalStatus approvalStatus,
    boolean available,
    boolean active,
    BigDecimal basePrice,
    UserMeResponseDTO user) {}
