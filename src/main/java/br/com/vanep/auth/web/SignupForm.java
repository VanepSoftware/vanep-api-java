package br.com.vanep.auth.web;

import br.com.vanep.auth.dto.DriverSignupFields;
import br.com.vanep.auth.validation.Cpf;
import br.com.vanep.user.enums.Gender;
import br.com.vanep.user.enums.UserType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

@Getter
@Setter
public class SignupForm implements DriverSignupFields {

  @NotNull(message = "{auth.signup.type.required}")
  private UserType type;

  private String name;

  @NotBlank(message = "{auth.signup.document.required}")
  @Cpf
  private String document;

  private String phone;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  private LocalDate birthDate;

  private Gender gender;

  @AssertTrue(message = "{auth.signup.terms.required}")
  private boolean acceptTerms;

  private String cnpj;

  private Integer experienceYears;

  private BigDecimal basePrice;

  /**
   * The Thymeleaf screen does not render the driver inputs yet, so choosing driver here fails
   * validation instead of creating an account without a driver record.
   */
  @AssertTrue(message = "{auth.signup.driver.fields.required}")
  public boolean isDriverFieldsComplete() {
    return type != UserType.DRIVER || (basePrice != null && basePrice.signum() > 0);
  }
}
