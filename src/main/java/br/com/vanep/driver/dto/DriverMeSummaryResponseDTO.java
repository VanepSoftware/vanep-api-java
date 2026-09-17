package br.com.vanep.driver.dto;

import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.user.dto.UserMeResponseDTO;
import java.math.BigDecimal;

public record DriverMeSummaryResponseDTO(
    String token,
    String photo,
    BigDecimal rating,
    DriverApprovalStatus approvalStatus,
    boolean available,
    boolean active,
    UserMeResponseDTO user) {}
