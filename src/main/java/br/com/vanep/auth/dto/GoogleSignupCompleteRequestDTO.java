package br.com.vanep.auth.dto;

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

/** Name and e-mail are not accepted here: both come from the ticket, which came from Google. */
@Getter
@Setter
public class GoogleSignupCompleteRequestDTO implements SignupCompletionFields {

  @NotBlank(message = "{auth.signup.ticket.required}")
  private String signupTicket;

  @NotNull(message = "{auth.signup.type.required}")
  private UserType type;

  @NotBlank(message = "{auth.signup.document.required}")
  @Cpf
  private String document;

  private String phone;

  private LocalDate birthDate;

  private Gender gender;

  @AssertTrue(message = "{auth.signup.terms.required}")
  private boolean acceptTerms;

  private String cnpj;

  private Integer experienceYears;

  private BigDecimal basePrice;

  @AssertTrue(message = "{auth.signup.basePrice.required}")
  public boolean isDriverFieldsComplete() {
    return type != UserType.DRIVER || (basePrice != null && basePrice.signum() > 0);
  }

  /** Admin is a valid {@link UserType}, but it is provisioned internally, never by sign-up. */
  @AssertTrue(message = "{auth.signup.type.notSelfService}")
  public boolean isSelfServiceType() {
    return type != UserType.ADMIN;
  }
}
