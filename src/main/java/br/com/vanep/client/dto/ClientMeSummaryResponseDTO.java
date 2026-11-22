package br.com.vanep.client.dto;

import br.com.vanep.address.dto.AddressResponseDTO;
import br.com.vanep.user.dto.UserMeResponseDTO;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

public record ClientMeSummaryResponseDTO(
    String token,
    @Nullable String photo,
    BigDecimal rating,
    boolean active,
    UserMeResponseDTO user,
    @Nullable AddressResponseDTO address) {}
