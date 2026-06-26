package br.com.vanep.auth.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Cadastro de motorista por e-mail/senha (campos adicionais ao {@link AccountSignupForm}). */
@Getter
@Setter
public class DriverSignupForm extends AccountSignupForm {

  private String cnpj;

  private Integer experienceYears;

  @NotBlank(message = "Informe a cidade de atuação.")
  private String city;

  @NotNull(message = "Informe o valor base.")
  @Positive(message = "O valor base deve ser positivo.")
  private BigDecimal basePrice;
}
