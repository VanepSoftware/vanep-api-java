package br.com.vanep.client.dto;

import br.com.vanep.user.dto.UserMeResponseDTO;
import java.math.BigDecimal;

public record ClientMeSummaryResponseDTO(
    String token, String photo, BigDecimal rating, boolean active, UserMeResponseDTO user) {}
