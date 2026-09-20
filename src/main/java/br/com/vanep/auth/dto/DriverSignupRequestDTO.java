package br.com.vanep.auth.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DriverSignupRequestDTO extends AccountSignupRequestDTO implements DriverSignupFields {
  private String cnpj;

  private Integer experienceYears;

  @NotNull(message = "{auth.signup.basePrice.required}")
  @Positive(message = "{auth.signup.basePrice.positive}")
  private BigDecimal basePrice;
}
